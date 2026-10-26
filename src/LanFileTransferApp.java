import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.text.*;
import java.util.*;
import java.util.List;

/** Simple LAN file transfer client/server. Java 17+, Swing only. */
public class LanFileTransferApp extends JFrame {
  private static final long serialVersionUID = 1L;
  private static final Color BLUE = new Color(35, 111, 210), GREEN = new Color(34, 139, 84);
  private static final Color ORANGE = new Color(190, 106, 15), TEXT = new Color(42, 51, 64);
  private final JTextField host = new JTextField("192.168.1.20");
  private final JTextField sendPort = new JTextField("5000");
  private final JTextField receivePort = new JTextField("5000");
  private final JTextField folder = new JTextField(System.getProperty("user.home") + "\\Downloads");
  private final JLabel selectedName = new JLabel("No file selected");
  private final JLabel selectedDetails = new JLabel("Drag a file here or browse your computer");
  private final JLabel sendStatus = new JLabel("Ready to send");
  private final JLabel receiveStatus = new JLabel("Server stopped");
  private final JLabel addressLabel = new JLabel();
  private final JLabel speedLabel = new JLabel("Speed: --");
  private final JLabel elapsedLabel = new JLabel("Elapsed: --");
  private final JProgressBar progress = new JProgressBar(0, 100);
  private final JButton sendButton = new JButton("➤  Send File");
  private final JButton cancelButton = new JButton("Cancel");
  private final JButton startButton = new JButton("▶  Start Receiving");
  private final JButton stopButton = new JButton("■  Stop Receiving");
  private final JRadioButton sendTcp = new JRadioButton("TCP (recommended)", true);
  private final JRadioButton sendUdp = new JRadioButton("UDP (experimental)");
  private final JRadioButton receiveTcp = new JRadioButton("TCP (recommended)", true);
  private final JRadioButton receiveUdp = new JRadioButton("UDP (experimental)");
  private final JCheckBox loss = new JCheckBox("Simulate 10% packet loss (demo)");
  private final DefaultTableModelModel receivedFiles = new DefaultTableModelModel();
  private final JTextArea log = new JTextArea();
  private volatile boolean on, cancelRequested;
  private transient ServerSocket ss;
  private transient DatagramSocket ds;
  private File chosen;

  static final int MAGIC = 0x434E4C55, START = 1, DATA = 2, ACK = 3, END = 4, RESULT = 5, CHUNK = 1000;

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> new LanFileTransferApp().setVisible(true));
  }

  LanFileTransferApp() {
    super("CNL  •  LAN File Transfer");
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setMinimumSize(new Dimension(900, 680));
    setSize(980, 720);
    setLocationByPlatform(true);
    getContentPane().setBackground(Color.WHITE);
    add(buildUi());
    cancelButton.setVisible(false);
    stopButton.setEnabled(false);
    say("Ready. Start receiving on one PC, then send from the other.");
  }

  private JComponent buildUi() {
    JPanel root = new JPanel(new BorderLayout(0, 12));
    root.setBorder(new EmptyBorder(18, 24, 18, 24));
    root.setBackground(Color.WHITE);
    JPanel heading = new JPanel(new BorderLayout());
    heading.setOpaque(false);
    JLabel title = new JLabel("LAN File Transfer");
    title.setFont(new Font("SansSerif", Font.BOLD, 26));
    title.setForeground(TEXT);
    JLabel subtitle = new JLabel("Fast, private transfers directly between computers on your local network");
    subtitle.setForeground(new Color(100, 110, 125));
    JPanel titles = new JPanel();
    titles.setOpaque(false);
    titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
    titles.add(title); titles.add(Box.createVerticalStrut(3)); titles.add(subtitle);
    heading.add(titles, BorderLayout.WEST);
    root.add(heading, BorderLayout.NORTH);
    JTabbedPane tabs = new JTabbedPane();
    tabs.setFont(new Font("SansSerif", Font.BOLD, 14));
    tabs.addTab("  Send File  ", buildSender());
    tabs.addTab("  Receive Files  ", buildReceiver());
    root.add(tabs, BorderLayout.CENTER);
    return root;
  }

  private JPanel buildSender() {
    JPanel page = page();
    JPanel left = card(new GridBagLayout());
    GridBagConstraints c = gbc();
    JLabel section = section("1  Select a file");
    addAt(left, section, c, 0, 0, 2);
    JPanel drop = new JPanel(new GridBagLayout());
    drop.setPreferredSize(new Dimension(400, 160));
    drop.setBackground(new Color(243, 248, 255));
    drop.setBorder(new CompoundBorder(new LineBorder(new Color(145, 185, 235), 1, true), new EmptyBorder(15, 15, 15, 15)));
    JLabel dropIcon = new JLabel("⇩");
    dropIcon.setFont(new Font("SansSerif", Font.BOLD, 34));
    dropIcon.setForeground(BLUE);
    drop.add(dropIcon, gbcAt(0, 0));
    JPanel fileText = new JPanel();
    fileText.setOpaque(false);
    fileText.setLayout(new BoxLayout(fileText, BoxLayout.Y_AXIS));
    selectedName.setFont(new Font("SansSerif", Font.BOLD, 15));
    selectedName.setForeground(TEXT);
    selectedDetails.setForeground(new Color(100, 110, 125));
    fileText.add(selectedName); fileText.add(Box.createVerticalStrut(5)); fileText.add(selectedDetails);
    drop.add(fileText, gbcAt(1, 0));
    drop.setTransferHandler(new FileDropHandler());
    addAt(left, drop, c, 0, 1, 2);
    JButton browse = new JButton("Browse Files");
    browse.addActionListener(e -> chooseFile());
    addAt(left, browse, c, 0, 2, 1);
    addAt(left, new JLabel("or drag and drop a file above"), c, 1, 2, 1);
    JLabel receiver = section("2  Receiver details");
    addAt(left, receiver, c, 0, 3, 2);
    addAt(left, label("Receiver LAN IP address"), c, 0, 4, 1);
    host.setToolTipText("Example: 192.168.1.20");
    addAt(left, host, c, 1, 4, 1);
    addAt(left, label("Port"), c, 0, 5, 1);
    addAt(left, sendPort, c, 1, 5, 1);
    addAt(left, label("Protocol"), c, 0, 6, 1);
    addAt(left, protocolPanel(sendTcp, sendUdp, loss), c, 1, 6, 1);
    page.add(left, BorderLayout.WEST);

    JPanel right = card(new GridBagLayout());
    GridBagConstraints r = gbc();
    addAt(right, section("3  Transfer"), r, 0, 0, 2);
    progress.setStringPainted(true);
    progress.setString("0%");
    progress.setForeground(BLUE);
    addAt(right, progress, r, 0, 1, 2);
    addAt(right, sendStatus, r, 0, 2, 2);
    JPanel stats = new JPanel(new GridLayout(1, 2, 20, 0));
    stats.setOpaque(false);
    stats.add(speedLabel); stats.add(elapsedLabel);
    addAt(right, stats, r, 0, 3, 2);
    sendButton.setFont(new Font("SansSerif", Font.BOLD, 16));
    sendButton.setBackground(BLUE); sendButton.setForeground(Color.WHITE); sendButton.setOpaque(true);
    sendButton.addActionListener(e -> send());
    cancelButton.addActionListener(e -> cancelRequested = true);
    addAt(right, sendButton, r, 0, 4, 2);
    addAt(right, cancelButton, r, 0, 5, 2);
    JLabel hint = new JLabel("<html><b>TCP</b> is reliable and recommended. <b>UDP</b> is experimental and intended for demonstrations.</html>");
    hint.setForeground(ORANGE);
    addAt(right, hint, r, 0, 6, 2);
    page.add(right, BorderLayout.CENTER);
    return page;
  }

  private JPanel buildReceiver() {
    JPanel page = page();
    JPanel top = card(new GridBagLayout());
    GridBagConstraints c = gbc();
    addAt(top, section("This computer"), c, 0, 0, 1);
    addAt(top, addressLabel, c, 0, 1, 1);
    addressLabel.setText("<html>LAN IPv4 address: <b>" + localAddress() + "</b><br><small>Share this address with the sender</small></html>");
    addAt(top, label("Destination folder"), c, 0, 2, 1);
    c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
    addAt(top, folder, c, 1, 2, 1);
    JButton browse = new JButton("Choose Folder");
    browse.addActionListener(e -> chooseFolder());
    addAt(top, browse, c, 2, 2, 1);
    addAt(top, label("Port"), c, 0, 3, 1);
    addAt(top, receivePort, c, 1, 3, 1);
    addAt(top, label("Protocol"), c, 0, 4, 1);
    addAt(top, protocolPanel(receiveTcp, receiveUdp, null), c, 1, 4, 2);
    startButton.setBackground(GREEN); startButton.setForeground(Color.WHITE); startButton.setOpaque(true);
    startButton.setFont(new Font("SansSerif", Font.BOLD, 15));
    stopButton.setForeground(new Color(170, 55, 55));
    startButton.addActionListener(e -> start());
    stopButton.addActionListener(e -> stop());
    addAt(top, startButton, c, 1, 5, 1); addAt(top, stopButton, c, 2, 5, 1);
    addAt(top, receiveStatus, c, 0, 5, 1);
    page.add(top, BorderLayout.NORTH);

    JPanel tableCard = card(new BorderLayout(0, 8));
    tableCard.add(section("Received files"), BorderLayout.NORTH);
    JTable table = new JTable(receivedFiles);
    table.setRowHeight(28); table.setFillsViewportHeight(true);
    table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
    tableCard.add(new JScrollPane(table), BorderLayout.CENTER);
    page.add(tableCard, BorderLayout.CENTER);
    return page;
  }

  private JPanel page() { JPanel p = new JPanel(new BorderLayout(14, 14)); p.setBorder(new EmptyBorder(16, 4, 4, 4)); p.setOpaque(false); return p; }
  private JPanel card(LayoutManager layout) { JPanel p = new JPanel(layout); p.setBackground(Color.WHITE); p.setBorder(new CompoundBorder(new LineBorder(new Color(220, 226, 234), 1, true), new EmptyBorder(18, 18, 18, 18))); return p; }
  private JLabel section(String text) { JLabel l = new JLabel(text); l.setFont(new Font("SansSerif", Font.BOLD, 17)); l.setForeground(TEXT); return l; }
  private JLabel label(String text) { JLabel l = new JLabel(text); l.setForeground(TEXT); return l; }
  private GridBagConstraints gbc() { GridBagConstraints c = new GridBagConstraints(); c.insets = new Insets(6, 6, 6, 6); c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; return c; }
  private GridBagConstraints gbcAt(int x, int y) { GridBagConstraints c = gbc(); c.gridx = x; c.gridy = y; c.weightx = x == 1 ? 1 : 0; return c; }
  private void addAt(JPanel p, Component x, GridBagConstraints template, int col, int row, int width) { GridBagConstraints c = (GridBagConstraints) template.clone(); c.gridx = col; c.gridy = row; c.gridwidth = width; p.add(x, c); }
  private JPanel protocolPanel(JRadioButton tcp, JRadioButton udp, JCheckBox extra) {
    JPanel p = new JPanel(); p.setOpaque(false); ButtonGroup group = new ButtonGroup(); group.add(tcp); group.add(udp); p.add(tcp); p.add(udp);
    if (extra != null) { extra.setForeground(ORANGE); extra.setVisible(false); udp.addActionListener(e -> extra.setVisible(udp.isSelected())); tcp.addActionListener(e -> extra.setVisible(false)); p.add(extra); }
    return p;
  }

  private void chooseFile() { JFileChooser c = new JFileChooser(); if (c.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) setChosen(c.getSelectedFile()); }
  private void setChosen(File f) { chosen = f; selectedName.setText(f.getName()); selectedDetails.setText(formatSize(f.length()) + "  •  " + fileType(f)); }
  private void chooseFolder() { JFileChooser c = new JFileChooser(folder.getText()); c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); if (c.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) folder.setText(c.getSelectedFile().getAbsolutePath()); }
  private String fileType(File f) { String n = f.getName(); int i = n.lastIndexOf('.'); return i > 0 ? n.substring(i + 1).toUpperCase(Locale.ROOT) + " file" : "File"; }
  private String formatSize(long n) { if (n < 1024) return n + " B"; if (n < 1048576) return String.format("%.1f KB", n / 1024.0); return String.format("%.1f MB", n / 1048576.0); }
  private String localAddress() { try { for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) for (InetAddress a : Collections.list(ni.getInetAddresses())) if (a instanceof Inet4Address && !a.isLoopbackAddress() && ni.isUp()) return a.getHostAddress(); } catch (Exception ignored) {} return "Not detected"; }
  private int parsePort(JTextField field) { int p = Integer.parseInt(field.getText().trim()); if (p < 1 || p > 65535) throw new IllegalArgumentException("Port must be between 1 and 65535."); return p; }
  private int p() { return parsePort(sendPort); }
  private int serverPort() { return parsePort(receivePort); }
  private boolean isTcp() { return sendTcp.isSelected(); }
  private void say(String text) { SwingUtilities.invokeLater(() -> { log.append(new SimpleDateFormat("HH:mm:ss").format(new Date()) + "  " + text + "\n"); }); }
  private void status(JLabel label, String text, Color color) { SwingUtilities.invokeLater(() -> { label.setText(text); label.setForeground(color); }); }
  private void prog(long n, long all) { SwingUtilities.invokeLater(() -> { int v = (int) Math.min(100, n * 100 / Math.max(1, all)); progress.setValue(v); progress.setString(v + "%"); }); }

  private void send() {
    if (chosen == null || !chosen.isFile()) { showError("Choose a file before sending."); return; }
    String address = host.getText().trim();
    try { InetAddress parsed = InetAddress.getByName(address); if (!(parsed instanceof Inet4Address)) throw new IllegalArgumentException("Use an IPv4 LAN address, for example 192.168.1.20."); p(); }
    catch (Exception e) { showError("Invalid receiver address or port: " + e.getMessage()); return; }
    cancelRequested = false; progress.setValue(0); progress.setString("0%"); sendButton.setEnabled(false); cancelButton.setVisible(true);
    status(sendStatus, "Connecting to " + address + "...", BLUE);
    new SwingWorker<Void, Void>() {
      protected Void doInBackground() throws Exception {
        try { if (isTcp()) sendTcp(); else sendUdp(); }
        catch (Exception e) { if (cancelRequested) throw new IOException("Transfer cancelled."); throw e; }
        return null;
      }
      protected void done() { sendButton.setEnabled(true); cancelButton.setVisible(false); try { get(); status(sendStatus, "Transfer completed and verified", GREEN); } catch (Exception e) { status(sendStatus, friendlyError(e), new Color(175, 55, 55)); say(friendlyError(e)); } }
    }.execute();
  }

  private String friendlyError(Exception e) {
    Throwable t = e; while (t.getCause() != null) t = t.getCause();
    if (t instanceof ConnectException) return "Receiver is not running or the port is blocked.";
    if (t instanceof SocketTimeoutException || (t.getMessage() != null && t.getMessage().toLowerCase(Locale.ROOT).contains("timeout"))) return "Timed out waiting for the receiver.";
    if (t.getMessage() != null && t.getMessage().toLowerCase(Locale.ROOT).contains("checksum")) return "Checksum verification failed.";
    return "Transfer failed: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
  }
  private void showError(String message) { status(sendStatus, message, new Color(175, 55, 55)); JOptionPane.showMessageDialog(this, message, "Transfer error", JOptionPane.ERROR_MESSAGE); }

  private void start() {
    if (on) return;
    try { Files.createDirectories(Path.of(folder.getText())); serverPort(); }
    catch (Exception e) { JOptionPane.showMessageDialog(this, "Choose a valid destination and port.\n" + e.getMessage(), "Receiver error", JOptionPane.ERROR_MESSAGE); return; }
    on = true; startButton.setEnabled(false); stopButton.setEnabled(true); receiveTcp.setEnabled(false); receiveUdp.setEnabled(false);
    boolean tcpMode = receiveTcp.isSelected();
    status(receiveStatus, "Waiting for files", BLUE); say("Starting " + (tcpMode ? "TCP" : "UDP") + " receiver on port " + receivePort.getText().trim());
    new Thread(() -> { try { if (tcpMode) tcpServer(); else udpServer(); } catch (Exception e) { if (on) { status(receiveStatus, "Server error: " + e.getMessage(), new Color(175, 55, 55)); say("Server error: " + e.getMessage()); } } finally { on = false; SwingUtilities.invokeLater(() -> { startButton.setEnabled(true); stopButton.setEnabled(false); receiveTcp.setEnabled(true); receiveUdp.setEnabled(true); }); } }, "receiver").start();
  }
  private void stop() { on = false; try { if (ss != null) ss.close(); if (ds != null) ds.close(); } catch (IOException e) { say("Could not stop receiver: " + e.getMessage()); } status(receiveStatus, "Server stopped", TEXT); say("Receiver stopped."); }

  // Existing tcpServer is connected to the Start Receiving button through start().
  private void tcpServer() throws Exception {
    ss = new ServerSocket(serverPort());
    while (on) try (Socket s = ss.accept()) {
      DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream())); DataOutputStream out = new DataOutputStream(s.getOutputStream());
      if (in.readInt() != 0x434E4C31) throw new IOException("Unknown TCP protocol");
      String name = safe(in.readUTF()); long size = in.readLong(); String wanted = in.readUTF();
      Path dir = Path.of(folder.getText()), tmp = dir.resolve(name + ".part"), target = dir.resolve(name);
      status(receiveStatus, "Receiving " + name, BLUE); copyIn(in, tmp, size); boolean ok = wanted.equals(hash(tmp));
      if (ok) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); addReceived(name, size, s.getInetAddress().getHostAddress(), "TCP", "Verified"); } else Files.deleteIfExists(tmp);
      out.writeBoolean(ok); out.writeUTF(ok ? "Saved: " + name : "Checksum mismatch"); out.flush();
      status(receiveStatus, ok ? "Completed" : "Checksum verification failed", ok ? GREEN : new Color(175, 55, 55));
    } catch (SocketException e) { if (on) throw e; }
  }

  // Existing sendTcp is connected to the Send File button through send().
  private void sendTcp() throws Exception {
    long begun = System.nanoTime(), size = chosen.length();
    try (Socket s = new Socket()) {
      s.connect(new InetSocketAddress(host.getText().trim(), p()), 5000);
      DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream())); DataInputStream in = new DataInputStream(s.getInputStream());
      out.writeInt(0x434E4C31); out.writeUTF(chosen.getName()); out.writeLong(size); out.writeUTF(hash(chosen.toPath())); copyOut(chosen.toPath(), out, size); out.flush();
      boolean ok = in.readBoolean(); String msg = in.readUTF(); say("TCP " + msg + "; " + stats(size, begun, 0)); if (!ok) throw new IOException("Checksum mismatch");
    }
  }

  // Existing udpServer is connected to the Start Receiving button through start().
  private void udpServer() throws Exception {
    ds = new DatagramSocket(serverPort()); ds.setSoTimeout(1000); Map<Integer, Session> sessions = new HashMap<>();
    try { while (on) try { Packet p = recv(ds); if (p == null) continue; if (p.type == START) { Session old = sessions.remove(p.id); if (old != null) old.close(); Session q = Session.create(p, folder.getText()); sessions.put(p.id, q); reply(ds, p, ACK, new byte[0]); status(receiveStatus, "Receiving " + q.name, BLUE); say("UDP receiving " + q.name); } else { Session q = sessions.get(p.id); if (q == null) continue; if (p.type == DATA) { q.take(p); reply(ds, p, ACK, new byte[0]); prog(q.got, q.size); } else if (p.type == END) { boolean ok = q.finish(new String(p.body, StandardCharsets.UTF_8)); reply(ds, p, RESULT, (ok ? "OK" : "FAIL").getBytes()); sessions.remove(p.id); if (ok) { addReceived(q.name, q.size, p.ip.getHostAddress(), "UDP", "Verified"); status(receiveStatus, "Completed", GREEN); } else status(receiveStatus, "Checksum verification failed", new Color(175, 55, 55)); } } } catch (SocketTimeoutException ignored) {} catch (SocketException e) { if (on) throw e; } } finally { for (Session q : sessions.values()) q.close(); }
  }

  // Existing sendUdp is connected to the Send File button through send().
  private void sendUdp() throws Exception {
    long begun = System.nanoTime(), size = chosen.length(); int id = new Random().nextInt(); String sum = hash(chosen.toPath()); InetAddress ip = InetAddress.getByName(host.getText().trim()); int tries = 0;
    try (DatagramSocket s = new DatagramSocket()) { s.setSoTimeout(700); tries += ack(s, ip, new Packet(START, id, -1, chosen.getName() + "\n" + size + "\n" + sum), false); byte[] b = new byte[CHUNK]; int seq = 0; long done = 0;
      try (InputStream in = new BufferedInputStream(new FileInputStream(chosen))) { for (int n; (n = in.read(b)) != -1;) { if (cancelRequested) throw new IOException("Transfer cancelled."); boolean drop = loss.isSelected() && new Random().nextInt(10) == 0; tries += ack(s, ip, new Packet(DATA, id, seq, Arrays.copyOf(b, n)), drop); done += n; prog(done, size); seq++; } }
      send(s, ip, new Packet(END, id, seq, sum)); Packet result = recv(s); if (result.type != RESULT || !"OK".equals(new String(result.body))) throw new IOException("Checksum mismatch"); say("UDP verified; " + stats(size, begun, tries));
    }
  }
  private int ack(DatagramSocket s, InetAddress ip, Packet packet, boolean pretendLost) throws Exception { int r = 0; for (int i = 0; i < 8; i++) { if (!(pretendLost && i == 0)) send(s, ip, packet); try { Packet x = recv(s); if (x != null && x.type == ACK && x.id == packet.id && x.seq == packet.seq) return r; } catch (SocketTimeoutException ignored) {} r++; } throw new IOException("UDP timeout at sequence " + packet.seq); }
  private void reply(DatagramSocket s, Packet received, int type, byte[] b) throws IOException { Packet x = new Packet(type, received.id, received.seq, b); byte[] raw = x.raw(); s.send(new DatagramPacket(raw, raw.length, received.ip, received.port)); }
  private void send(DatagramSocket s, InetAddress ip, Packet x) throws IOException { byte[] raw = x.raw(); s.send(new DatagramPacket(raw, raw.length, ip, p())); }
  private static Packet recv(DatagramSocket s) throws IOException { byte[] b = new byte[1400]; DatagramPacket d = new DatagramPacket(b, b.length); s.receive(d); return Packet.read(d); }
  private void copyIn(InputStream in, Path to, long all) throws Exception { try (OutputStream o = new BufferedOutputStream(Files.newOutputStream(to))) { byte[] b = new byte[32768]; long n = 0; while (n < all) { int z = in.read(b, 0, (int) Math.min(b.length, all - n)); if (z < 0) throw new EOFException(); o.write(b, 0, z); n += z; prog(n, all); } } }
  private void copyOut(Path from, OutputStream o, long all) throws Exception { try (InputStream in = new BufferedInputStream(Files.newInputStream(from))) { byte[] b = new byte[32768]; long n = 0; for (int z; (z = in.read(b)) != -1;) { if (cancelRequested) throw new IOException("Transfer cancelled."); o.write(b, 0, z); n += z; prog(n, all); } } }
  private static String hash(Path f) throws Exception { MessageDigest d = MessageDigest.getInstance("SHA-256"); try (InputStream i = Files.newInputStream(f)) { byte[] b = new byte[32768]; for (int n; (n = i.read(b)) != -1;) d.update(b, 0, n); } StringBuilder s = new StringBuilder(); for (byte x : d.digest()) s.append(String.format("%02x", x)); return s.toString(); }
  private static String safe(String n) { return Path.of(n).getFileName().toString().replaceAll("[^A-Za-z0-9._ -]", "_"); }
  private String stats(long bytes, long start, int retry) { double sec = Math.max(.001, (System.nanoTime() - start) / 1e9); SwingUtilities.invokeLater(() -> { speedLabel.setText(String.format("Speed: %.2f MB/s", bytes / 1e6 / sec)); elapsedLabel.setText(String.format("Elapsed: %.2f s", sec)); }); return String.format("%.2f MB/s; %.2f s; retries=%d", bytes / 1e6 / sec, sec, retry); }
  private void addReceived(String name, long size, String sender, String protocol, String verification) { SwingUtilities.invokeLater(() -> receivedFiles.addRow(new Object[]{name, formatSize(size), sender, protocol, new SimpleDateFormat("HH:mm:ss").format(new Date()), verification})); }

  private final class FileDropHandler extends TransferHandler {
    private static final long serialVersionUID = 1L;
    public boolean canImport(TransferSupport s) { return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor); }
    public boolean importData(TransferSupport s) { try { List<?> files = (List<?>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor); if (!files.isEmpty() && files.get(0) instanceof File) { setChosen((File) files.get(0)); return true; } } catch (Exception e) { say("Could not read dropped file: " + e.getMessage()); } return false; }
  }
  private static final class DefaultTableModelModel extends javax.swing.table.DefaultTableModel {
    private static final long serialVersionUID = 1L;
    DefaultTableModelModel() { super(new Object[]{"File name", "Size", "Sender IP", "Protocol", "Time", "Verification"}, 0); }
    public boolean isCellEditable(int r, int c) { return false; }
  }
  private static class Packet {
    int type, id, seq, port; InetAddress ip; byte[] body;
    Packet(int t, int i, int q, String s) { this(t, i, q, s.getBytes(StandardCharsets.UTF_8)); }
    Packet(int t, int i, int q, byte[] b) { type = t; id = i; seq = q; body = b; }
    byte[] raw() throws IOException { ByteArrayOutputStream x = new ByteArrayOutputStream(); DataOutputStream o = new DataOutputStream(x); o.writeInt(MAGIC); o.writeByte(type); o.writeInt(id); o.writeInt(seq); o.writeShort(body.length); o.write(body); return x.toByteArray(); }
    static Packet read(DatagramPacket d) throws IOException { DataInputStream i = new DataInputStream(new ByteArrayInputStream(d.getData(), 0, d.getLength())); if (i.readInt() != MAGIC) return null; int t = i.readUnsignedByte(), id = i.readInt(), q = i.readInt(), n = i.readUnsignedShort(); if (n > i.available()) return null; Packet p = new Packet(t, id, q, i.readNBytes(n)); p.ip = d.getAddress(); p.port = d.getPort(); return p; }
  }
  private static class Session {
    String name, wanted; long size, got; int next; Path part, target; OutputStream out;
    static Session create(Packet p, String dir) throws Exception { String[] a = new String(p.body, StandardCharsets.UTF_8).split("\\n", 3); if (a.length != 3) throw new IOException("Bad metadata"); return new Session(safe(a[0]), Long.parseLong(a[1]), a[2], Path.of(dir, safe(a[0]))); }
    Session(String n, long s, String h, Path t) throws Exception { name = n; size = s; wanted = h; target = t; part = Path.of(t + ".part"); out = new BufferedOutputStream(Files.newOutputStream(part)); }
    void take(Packet p) throws IOException { if (p.seq == next) { out.write(p.body); got += p.body.length; next++; } }
    boolean finish(String endHash) throws Exception { out.close(); boolean ok = got == size && wanted.equals(endHash) && wanted.equals(hash(part)); if (ok) Files.move(part, target, StandardCopyOption.REPLACE_EXISTING); else Files.deleteIfExists(part); return ok; }
    void close() { try { out.close(); Files.deleteIfExists(part); } catch (Exception ignored) {} }
  }
}
