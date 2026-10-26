import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Dependency-free CNL remote file sharing web server. */
public class LanFileSharingWebServer {
  static final int PORT = 8080;
  static final long MAX_FILE_SIZE = 25L * 1024 * 1024;
  static final long EXPIRY_MILLIS = 24L * 60 * 60 * 1000;
  static final long ONLINE_MILLIS = 45_000;
  static final Path STORAGE = Path.of("shared-files");
  static final Map<String, User> USERS = new ConcurrentHashMap<>();
  static final Map<String, SharedFile> FILES = new ConcurrentHashMap<>();
  static final Map<String, List<Activity>> ACTIVITIES = new ConcurrentHashMap<>();
  static final Set<String> ALLOWED_EXTENSIONS = Set.of(
      "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "rtf",
      "txt", "csv", "md", "jpg", "jpeg", "png", "gif", "webp", "svg",
      "zip", "rar", "7z", "tar", "gz");
  static final Set<String> BLOCKED_EXTENSIONS = Set.of("exe", "bat", "cmd", "msi", "jar");

  record User(String id, String name, String address, long seen) {}
  record SharedFile(String id, String fromId, String fromName, String toId, String toName,
                    String originalName, Path path, long size, String checksum,
                    long uploadedAt, long expiresAt) {}
  record Activity(String fileName, String otherUser, long size, String time,
                  String direction, String status) {}

  public static void main(String[] args) throws Exception {
    Files.createDirectories(STORAGE);
    ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor();
    cleanup.scheduleAtFixedRate(LanFileSharingWebServer::removeExpiredFiles, 1, 1, TimeUnit.HOURS);
    HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
    server.createContext("/", LanFileSharingWebServer::page);
    server.createContext("/api/join", LanFileSharingWebServer::join);
    server.createContext("/api/leave", LanFileSharingWebServer::leave);
    server.createContext("/api/users", LanFileSharingWebServer::users);
    server.createContext("/api/send", LanFileSharingWebServer::send);
    server.createContext("/api/inbox", LanFileSharingWebServer::inbox);
    server.createContext("/api/activity", LanFileSharingWebServer::activity);
    server.createContext("/download", LanFileSharingWebServer::download);
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
    System.out.println("LAN Web Share started.");
    System.out.println("Open http://" + localIp() + ":" + PORT + " on every LAN computer.");
    System.out.println("Local HTTP is not encrypted. Press Ctrl+C to stop.");
  }

  static void page(HttpExchange x) throws IOException {
    if (!"GET".equals(x.getRequestMethod())) { error(x, 405, "Method not allowed."); return; }
    respond(x, 200, "text/html; charset=utf-8", HTML);
  }

  static void join(HttpExchange x) throws IOException {
    if (!"POST".equals(x.getRequestMethod())) { error(x, 405, "Method not allowed."); return; }
    String name = query(x).getOrDefault("name", "").trim();
    if (!name.matches("[A-Za-z0-9][A-Za-z0-9 _.-]{0,29}")) {
      error(x, 400, "Use a unique name with up to 30 letters, numbers, spaces, dots, hyphens, or underscores.");
      return;
    }
    for (User user : USERS.values()) {
      if (user.name().equalsIgnoreCase(name) && isOnline(user)) {
        error(x, 409, "That display name is already online. Choose another name.");
        return;
      }
    }
    String id = UUID.randomUUID().toString();
    USERS.put(id, new User(id, name, x.getRemoteAddress().getAddress().getHostAddress(), System.currentTimeMillis()));
    ACTIVITIES.put(id, new CopyOnWriteArrayList<>());
    json(x, "{\"id\":\"" + id + "\",\"name\":\"" + esc(name) + "\"}");
  }

  static void leave(HttpExchange x) throws IOException {
    if (!"POST".equals(x.getRequestMethod())) { error(x, 405, "Method not allowed."); return; }
    String id = query(x).getOrDefault("me", "");
    USERS.remove(id);
    json(x, "{\"ok\":true}");
  }

  static void users(HttpExchange x) throws IOException {
    if (!"GET".equals(x.getRequestMethod())) { error(x, 405, "Method not allowed."); return; }
    String me = query(x).getOrDefault("me", "");
    if (!touch(me)) { error(x, 401, "Your session has ended. Please join again."); return; }
    removeOfflineUsers();
    StringBuilder b = new StringBuilder("[");
    boolean first = true;
    for (User u : USERS.values()) if (!u.id().equals(me)) {
      if (!first) b.append(',');
      first = false;
      b.append("{\"id\":\"").append(u.id()).append("\",\"name\":\"").append(esc(u.name())).append("\"}");
    }
    json(x, b.append(']').toString());
  }

  static void send(HttpExchange x) throws IOException {
    if (!"POST".equals(x.getRequestMethod())) { error(x, 405, "Method not allowed."); return; }
    Map<String, String> q = query(x);
    String from = q.getOrDefault("from", ""), to = q.getOrDefault("to", "");
    String name = validateFilename(q.getOrDefault("name", ""));
    User sender = USERS.get(from), receiver = USERS.get(to);
    if (sender == null || !touch(from)) { error(x, 401, "Your session has ended. Please join again."); return; }
    if (receiver == null || !isOnline(receiver)) { error(x, 409, "The selected recipient is offline."); return; }
    if (name == null) { error(x, 400, "This filename is unsafe or its file type is not allowed."); return; }
    String length = x.getRequestHeaders().getFirst("Content-Length");
    if (length != null && parseLong(length) > MAX_FILE_SIZE) {
      error(x, 413, "Files must be 25 MB or smaller."); return;
    }
    String id = UUID.randomUUID().toString();
    Path dir = STORAGE.resolve(to);
    Path stored = dir.resolve(id + "_" + name);
    long bytes = 0;
    try {
      Files.createDirectories(dir);
      try (InputStream in = x.getRequestBody(); OutputStream out = Files.newOutputStream(stored)) {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
          bytes += read;
          if (bytes > MAX_FILE_SIZE) {
            Files.deleteIfExists(stored);
            error(x, 413, "Files must be 25 MB or smaller."); return;
          }
          out.write(buffer, 0, read);
        }
      }
      String checksum = sha256(stored);
      long now = System.currentTimeMillis();
      FILES.put(id, new SharedFile(id, from, sender.name(), to, receiver.name(), name,
          stored, bytes, checksum, now, now + EXPIRY_MILLIS));
      addActivity(from, new Activity(name, receiver.name(), bytes, iso(now), "Sent", "Success"));
      addActivity(to, new Activity(name, sender.name(), bytes, iso(now), "Received", "Available"));
      json(x, "{\"ok\":true,\"id\":\"" + id + "\"}");
    } catch (IOException | GeneralSecurityException e) {
      Files.deleteIfExists(stored);
      error(x, 500, "The server could not store this file.");
    }
  }

  static void inbox(HttpExchange x) throws IOException {
    if (!"GET".equals(x.getRequestMethod())) { error(x, 405, "Method not allowed."); return; }
    String me = query(x).getOrDefault("me", "");
    if (!touch(me)) { error(x, 401, "Your session has ended. Please join again."); return; }
    StringBuilder b = new StringBuilder("[");
    boolean first = true;
    for (SharedFile f : FILES.values()) if (f.toId().equals(me) && f.expiresAt() > System.currentTimeMillis()) {
      if (!first) b.append(',');
      first = false;
      b.append("{\"id\":\"").append(f.id()).append("\",\"from\":\"").append(esc(f.fromName()))
          .append("\",\"name\":\"").append(esc(f.originalName())).append("\",\"size\":").append(f.size())
          .append(",\"time\":\"").append(esc(iso(f.uploadedAt()))).append("\",\"expiresAt\":").append(f.expiresAt()).append('}');
    }
    json(x, b.append(']').toString());
  }

  static void activity(HttpExchange x) throws IOException {
    if (!"GET".equals(x.getRequestMethod())) { error(x, 405, "Method not allowed."); return; }
    String me = query(x).getOrDefault("me", "");
    if (!touch(me)) { error(x, 401, "Your session has ended. Please join again."); return; }
    StringBuilder b = new StringBuilder("[");
    boolean first = true;
    for (Activity a : ACTIVITIES.getOrDefault(me, List.of())) {
      if (!first) b.append(',');
      first = false;
      b.append("{\"name\":\"").append(esc(a.fileName())).append("\",\"other\":\"").append(esc(a.otherUser()))
          .append("\",\"size\":").append(a.size()).append(",\"time\":\"").append(esc(a.time()))
          .append("\",\"direction\":\"").append(a.direction()).append("\",\"status\":\"").append(a.status()).append("\"}");
    }
    json(x, b.append(']').toString());
  }

  static void download(HttpExchange x) throws IOException {
    if (!"GET".equals(x.getRequestMethod())) { error(x, 405, "Method not allowed."); return; }
    Map<String, String> q = query(x);
    String me = q.getOrDefault("me", "");
    if (!touch(me)) { error(x, 401, "Your session has ended. Please join again."); return; }
    SharedFile f = FILES.get(q.getOrDefault("id", ""));
    if (f == null || !f.toId().equals(me) || f.expiresAt() <= System.currentTimeMillis()) {
      error(x, 404, "File not found or it has expired."); return;
    }
    try {
      if (!Files.exists(f.path()) || !sha256(f.path()).equals(f.checksum())) {
        addActivity(me, new Activity(f.originalName(), f.fromName(), f.size(), iso(System.currentTimeMillis()), "Received", "Integrity error"));
        error(x, 409, "Integrity error: the file checksum no longer matches.");
        return;
      }
      Headers h = x.getResponseHeaders();
      h.set("Content-Type", "application/octet-stream");
      h.set("Content-Disposition", "attachment; filename=\"" + f.originalName().replace("\"", "") + "\"");
      x.sendResponseHeaders(200, f.size());
      try (OutputStream out = x.getResponseBody()) { Files.copy(f.path(), out); }
    } catch (GeneralSecurityException e) {
      error(x, 500, "The server could not verify file integrity.");
    }
  }

  static void removeExpiredFiles() {
    long now = System.currentTimeMillis();
    FILES.values().removeIf(f -> {
      if (f.expiresAt() > now) return false;
      try { Files.deleteIfExists(f.path()); } catch (IOException e) {
        System.err.println("Could not delete expired file " + f.path() + ": " + e.getMessage());
      }
      return true;
    });
  }

  static void removeOfflineUsers() { USERS.entrySet().removeIf(e -> !isOnline(e.getValue())); }
  static boolean isOnline(User u) { return u.seen() >= System.currentTimeMillis() - ONLINE_MILLIS; }
  static boolean touch(String id) {
    User u = USERS.get(id);
    if (u == null || !isOnline(u)) return false;
    USERS.put(id, new User(u.id(), u.name(), u.address(), System.currentTimeMillis()));
    return true;
  }
  static void addActivity(String id, Activity activity) {
    ACTIVITIES.computeIfAbsent(id, ignored -> new CopyOnWriteArrayList<>()).add(activity);
  }
  static String validateFilename(String value) {
    if (value.isBlank() || value.contains("/") || value.contains("\\") || value.contains("..")) return null;
    String name = Paths.get(value).getFileName().toString();
    if (!name.equals(value) || !name.matches("[A-Za-z0-9][A-Za-z0-9 ._()-]{0,119}")) return null;
    int dot = name.lastIndexOf('.');
    String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    return BLOCKED_EXTENSIONS.contains(ext) || !ALLOWED_EXTENSIONS.contains(ext) ? null : name;
  }
  static String sha256(Path path) throws IOException, GeneralSecurityException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream in = Files.newInputStream(path)) {
      byte[] buffer = new byte[8192]; int read;
      while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
    }
    StringBuilder result = new StringBuilder();
    for (byte b : digest.digest()) result.append(String.format("%02x", b));
    return result.toString();
  }
  static long parseLong(String value) { try { return Long.parseLong(value); } catch (NumberFormatException e) { return -1; } }
  static String iso(long millis) { return Instant.ofEpochMilli(millis).toString(); }
  static Map<String, String> query(HttpExchange x) {
    Map<String, String> result = new HashMap<>(); String raw = x.getRequestURI().getRawQuery();
    if (raw == null) return result;
    for (String pair : raw.split("&")) { String[] parts = pair.split("=", 2);
      result.put(decode(parts[0]), parts.length > 1 ? decode(parts[1]) : ""); }
    return result;
  }
  static String decode(String value) { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
  static String esc(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " "); }
  static void error(HttpExchange x, int code, String message) throws IOException { json(x, "{\"error\":\"" + esc(message) + "\"}", code); }
  static void json(HttpExchange x, String body) throws IOException { json(x, body, 200); }
  static void json(HttpExchange x, String body, int code) throws IOException { respond(x, code, "application/json; charset=utf-8", body); }
  static void respond(HttpExchange x, int code, String type, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8); x.getResponseHeaders().set("Content-Type", type);
    x.sendResponseHeaders(code, bytes.length); try (OutputStream out = x.getResponseBody()) { out.write(bytes); }
  }
  static String localIp() {
    try {
      Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
      while (all.hasMoreElements()) { NetworkInterface n = all.nextElement();
        if (!n.isUp() || n.isLoopback()) continue;
        for (InterfaceAddress a : n.getInterfaceAddresses())
          if (a.getAddress() instanceof Inet4Address && !a.getAddress().isLoopbackAddress()) return a.getAddress().getHostAddress();
      }
    } catch (SocketException e) { System.err.println("Could not determine LAN address: " + e.getMessage()); }
    return "SERVER-IP";
  }

  static final String HTML = """
<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>
<title>CNL Remote File Share</title><style>
*{box-sizing:border-box}body{margin:0;background:#f4f7fb;color:#172b4d;font:16px Arial,sans-serif}.top{background:#114b73;color:#fff;padding:18px 8%;font-size:23px;font-weight:bold}.wrap{max-width:1100px;margin:28px auto;padding:0 20px}.card{background:#fff;border:1px solid #d7e1ed;border-radius:12px;padding:22px;margin:16px 0;box-shadow:0 2px 8px #1231}.row{display:flex;gap:18px;flex-wrap:wrap}.col{flex:1;min-width:280px}input,button{font:inherit;padding:11px;border:1px solid #aac0d4;border-radius:7px}input{width:100%;margin:7px 0 15px}button{background:#176fc6;color:#fff;border:0;font-weight:bold;cursor:pointer}.green{background:#198754}.danger{background:#bd3434}.hidden{display:none}.muted{color:#60758d}.good{color:#157347}.bad{color:#b02a37}.user{display:flex;justify-content:space-between;align-items:center;border:1px solid #d7e1ed;border-radius:8px;padding:10px;margin:7px 0}.bar{height:16px;background:#e5ecf3;border-radius:8px;overflow:hidden}.bar i{display:block;height:100%;width:0;background:#176fc6}table{width:100%;border-collapse:collapse;display:block;overflow-x:auto}td,th{padding:9px;border-bottom:1px solid #e2e8f0;text-align:left;white-space:nowrap}.tabs{display:flex;gap:8px;flex-wrap:wrap;margin:15px 0}.tabs button{background:#dceaf6;color:#17466b}.tabs button.active{background:#176fc6;color:#fff}.section{display:none}.section.active{display:block}@media(max-width:600px){.wrap{padding:0 12px}.card{padding:16px}}
</style></head><body><div class='top'>CNL • Remote File Share</div><main class='wrap'>
<section id='joinCard' class='card'><h1>Join the file-sharing room</h1><p class='muted'>Choose a unique display name. Users and files stay in memory for this server session.</p><label>Your display name</label><input id='name' maxlength='30' placeholder='Example: Arhat-PC'><button onclick='join()'>Join</button><p id='joinMsg' class='bad'></p></section>
<section id='app' class='hidden'><div class='card'><div class='row'><div class='col'><h1>Welcome, <span id='meName'></span></h1><p class='muted'>Logged in for this server session.</p></div><div><button class='danger' onclick='leave()'>Leave</button></div></div></div>
<div class='tabs'><button class='active' onclick="tab('online',this)">Online Users</button><button onclick="tab('send',this)">Send File</button><button onclick="tab('received',this)">Received Files</button><button onclick="tab('activity',this)">My Activity</button></div>
<section id='online' class='section active card'><h2>Online Users</h2><p class='muted'>Select one recipient before sending.</p><div id='users'>Loading...</div></section>
<section id='send' class='section card'><h2>Send File</h2><p id='recipient' class='muted'>Select a recipient in Online Users.</p><input type='file' id='file' accept='.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.odt,.rtf,.txt,.csv,.md,.jpg,.jpeg,.png,.gif,.webp,.svg,.zip,.rar,.7z,.tar,.gz'><button id='sendBtn' class='green' onclick='sendFile()' disabled>Upload and Send</button><p class='muted'>Maximum 25 MB. Documents, images, archives, and text files are allowed. Executables are blocked.</p><div class='bar'><i id='bar'></i></div><p id='sendMsg'></p></section>
<section id='received' class='section card'><h2>My received files</h2><table><thead><tr><th>From</th><th>File</th><th>Size</th><th>Expiry</th><th>Action</th></tr></thead><tbody id='inbox'></tbody></table></section>
<section id='activity' class='section card'><h2>My activity</h2><table><thead><tr><th>Direction</th><th>File</th><th>Other user</th><th>Size</th><th>Time</th><th>Status</th></tr></thead><tbody id='activities'></tbody></table></section></section></main>
<script>
let me=localStorage.getItem('lanShareId')||'',meName=localStorage.getItem('lanShareName')||'',selected='';
const $=id=>document.getElementById(id),api=async(u,o)=>{let r=await fetch(u,o),j=await r.json();if(!r.ok||j.error)throw Error(j.error||'Request failed.');return j};
async function join(){try{let j=await api('/api/join?name='+encodeURIComponent($('name').value),{method:'POST'});me=j.id;meName=j.name;localStorage.setItem('lanShareId',me);localStorage.setItem('lanShareName',meName);show()}catch(e){$('joinMsg').textContent=e.message}}
async function leave(){try{await api('/api/leave?me='+me,{method:'POST'})}finally{localStorage.removeItem('lanShareId');localStorage.removeItem('lanShareName');location.reload()}}
function show(){if(!me)return;$('joinCard').classList.add('hidden');$('app').classList.remove('hidden');$('meName').textContent=meName;refresh();setInterval(refresh,5000)}
function tab(id,b){document.querySelectorAll('.section').forEach(x=>x.classList.remove('active'));$(id).classList.add('active');document.querySelectorAll('.tabs button').forEach(x=>x.classList.remove('active'));b.classList.add('active')}
async function refresh(){try{let users=await api('/api/users?me='+encodeURIComponent(me));$('users').innerHTML=users.length?users.map(u=>`<div class='user'><b>${esc(u.name)}</b><button onclick="selectUser('${u.id}','${esc(u.name)}')">Select</button></div>`).join(''):'<p class="muted">No other users are online.</p>';let files=await api('/api/inbox?me='+encodeURIComponent(me));$('inbox').innerHTML=files.length?files.map(f=>`<tr><td>${esc(f.from)}</td><td>${esc(f.name)}</td><td>${size(f.size)}</td><td>Expires in approximately 24 hours</td><td><button onclick="download('${f.id}')">Download</button></td></tr>`).join(''):'<tr><td colspan="5" class="muted">No files received yet.</td></tr>';let a=await api('/api/activity?me='+encodeURIComponent(me));$('activities').innerHTML=a.length?a.map(x=>`<tr><td>${x.direction}</td><td>${esc(x.name)}</td><td>${esc(x.other)}</td><td>${size(x.size)}</td><td>${new Date(x.time).toLocaleString()}</td><td>${esc(x.status)}</td></tr>`).join(''):'<tr><td colspan="6" class="muted">No activity yet.</td></tr>'}catch(e){if(e.message.includes('session')){localStorage.clear();location.reload()}else console.log(e)}}
function selectUser(id,name){selected=id;$('recipient').innerHTML='Sending to: <b>'+esc(name)+'</b>';$('sendBtn').disabled=false;tab('send',document.querySelectorAll('.tabs button')[1])}
function sendFile(){let f=$('file').files[0];if(!f||!selected){$('sendMsg').textContent='Choose a recipient and a file.';return}if(f.size>25*1024*1024){$('sendMsg').className='bad';$('sendMsg').textContent='Files must be 25 MB or smaller.';return}$('sendMsg').textContent='Uploading...';$('bar').style.width='0';let x=new XMLHttpRequest();x.open('POST','/api/send?from='+encodeURIComponent(me)+'&to='+encodeURIComponent(selected)+'&name='+encodeURIComponent(f.name));x.upload.onprogress=e=>{if(e.lengthComputable)$('bar').style.width=(e.loaded*100/e.total)+'%'};x.onload=()=>{let j;try{j=JSON.parse(x.responseText)}catch(e){j={}}$('sendMsg').className=x.status===200?'good':'bad';$('sendMsg').textContent=x.status===200?'File shared successfully.':(j.error||'Upload failed.');if(x.status===200){$('bar').style.width='100%';refresh()}};x.onerror=()=>{$('sendMsg').className='bad';$('sendMsg').textContent='Could not reach the server.'};x.send(f)}
async function download(id){try{let r=await fetch('/download?id='+encodeURIComponent(id)+'&me='+encodeURIComponent(me));if(!r.ok){let j=await r.json();throw Error(j.error||'Download failed.')}let cd=r.headers.get('Content-Disposition')||'',match=cd.match(/filename="([^"]+)"/),blob=await r.blob(),a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=match?match[1]:'download';a.click();URL.revokeObjectURL(a.href)}catch(e){alert(e.message)}}
function esc(s){let d=document.createElement('div');d.textContent=s;return d.innerHTML}function size(n){return n<1024?n+' B':n<1048576?(n/1024).toFixed(1)+' KB':(n/1048576).toFixed(1)+' MB'}show();
</script></body></html>""";
}
