import React, { useEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import {
  ArrowDownToLine, ArrowRight, Check, ChevronDown, CircleAlert, Clock3, Copy,
  Download, FileArchive, FileText, FolderOpen, Inbox, Link2, Loader2, LogOut,
  Menu, MessageCircle, MoreHorizontal, Paperclip, Plus, RefreshCw, Search,
  Send, Server, ShieldCheck, Sparkles, UploadCloud, Users, X, Zap
} from 'lucide-react';
import './styles.css';

const API_URL = (import.meta.env.VITE_API_URL || '').replace(/\/api\/?$/, '').replace(/\/$/, '');
const sessionKey = 'shareroom-session';

const api = async (path, options = {}) => {
  const response = await fetch(`${API_URL}/api${path}`, options);
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.message || data.error || `Request failed (${response.status})`);
  return data;
};

const formatBytes = (bytes = 0) => {
  if (!bytes) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / 1024 ** index).toFixed(index ? 1 : 0)} ${units[index]}`;
};
const initials = (name = '?') => name.split(/\s+/).map((part) => part[0]).join('').slice(0, 2).toUpperCase();
const fileIcon = (name = '') => name.toLowerCase().endsWith('.zip') ? FileArchive : FileText;

function Toasts({ items, remove }) {
  return <div className="toast-stack" aria-live="polite">{items.map((toast) => (
    <motion.div key={toast.id} initial={{ opacity: 0, x: 24 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 24 }} className={`toast ${toast.type || ''}`}>
      {toast.type === 'error' ? <CircleAlert size={18} /> : <Check size={18} />}<span>{toast.message}</span>
      <button onClick={() => remove(toast.id)} aria-label="Dismiss"><X size={15} /></button>
    </motion.div>
  ))}</div>;
}

function Avatar({ name, online = false, size = 'md' }) {
  return <span className={`avatar avatar-${size}`}><span>{initials(name)}</span>{online && <i />}</span>;
}

function Landing({ onStarted, notify }) {
  const [mode, setMode] = useState('create');
  const [name, setName] = useState('');
  const [roomName, setRoomName] = useState('');
  const [roomCode, setRoomCode] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = async (event) => {
    event.preventDefault();
    if (!name.trim() || (mode === 'join' && !roomCode.trim())) return;
    setBusy(true);
    try {
      const data = mode === 'create'
        ? await api('/rooms', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: name.trim(), roomName: roomName.trim() || null }) })
        : await api(`/rooms/${encodeURIComponent(roomCode.trim())}/join`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: name.trim() }) });
      onStarted({ ...data, roomId: data.roomId || data.id || roomCode.trim(), memberId: data.memberId || data.member?.id, member: data.member || { name: name.trim() } });
    } catch (error) { notify(error.message, 'error'); } finally { setBusy(false); }
  };
  return <main className="landing">
    <nav className="topbar"><div className="brand"><span className="brand-mark"><Link2 size={19} /></span><span>share<span>room</span></span></div><div className="secure-label"><ShieldCheck size={16} /> Private by design</div></nav>
    <section className="hero">
      <div className="hero-copy">
        <div className="eyebrow"><Sparkles size={14} /> SIMPLE, PRIVATE FILE SHARING</div>
        <h1>Move files.<br /><em>Not accounts.</em></h1>
        <p className="hero-lede">A quiet room for your team to share what matters. No sign-ups, no clutter, just a secure space that works.</p>
        <div className="hero-proof"><span><ShieldCheck size={16} /> No account required</span><span><Zap size={16} /> Instant transfer</span></div>
      </div>
      <motion.div className="room-card" initial={{ opacity: 0, y: 18 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: .5 }}>
        <div className="card-heading"><div><span className="mini-kicker">GET STARTED</span><h2>{mode === 'create' ? 'Create a room' : 'Join a room'}</h2></div><div className="round-icon"><Users size={20} /></div></div>
        <div className="segmented"><button className={mode === 'create' ? 'active' : ''} onClick={() => setMode('create')}>Create new</button><button className={mode === 'join' ? 'active' : ''} onClick={() => setMode('join')}>Join existing</button></div>
        <form onSubmit={submit}>
          <label>Your display name<input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Alex Morgan" autoFocus maxLength={40} /></label>
          {mode === 'create' && <label>Room name <span className="optional">(optional)</span><input value={roomName} onChange={(e) => setRoomName(e.target.value)} placeholder="e.g. Design handoff" maxLength={100} /></label>}
          <AnimatePresence initial={false}>{mode === 'join' && <motion.label initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }}>Room code<input value={roomCode} onChange={(e) => setRoomCode(e.target.value)} placeholder="e.g. SPRING-FOX-42" /></motion.label>}</AnimatePresence>
          <button className="primary-button wide" disabled={busy || !name.trim() || (mode === 'join' && !roomCode.trim())}>{busy ? <Loader2 className="spin" size={18} /> : mode === 'create' ? <Plus size={18} /> : null} {busy ? 'Opening room…' : mode === 'create' ? 'Create room' : 'Join room'} {!busy && <ArrowRight size={17} />}</button>
        </form>
        <p className="card-footnote"><ShieldCheck size={14} /> Files are temporary and expire automatically</p>
      </motion.div>
    </section>
    <footer><span>© {new Date().getFullYear()} ShareRoom</span><span>Built for focused collaboration</span></footer>
  </main>;
}

function EmptyState({ icon: Icon = Inbox, title, text, action }) {
  return <div className="empty-state"><span className="empty-icon"><Icon size={24} /></span><h3>{title}</h3><p>{text}</p>{action}</div>;
}

function Dashboard({ session, onLeave, notify }) {
  const reduceMotion = useReducedMotion();
  const [room, setRoom] = useState(session);
  const [members, setMembers] = useState([]);
  const [inbox, setInbox] = useState([]);
  const [activity, setActivity] = useState([]);
  const [recipient, setRecipient] = useState(null);
  const [files, setFiles] = useState([]);
  const [uploads, setUploads] = useState([]);
  const [dragging, setDragging] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeTab, setActiveTab] = useState('inbox');
  const [mobileMenu, setMobileMenu] = useState(false);
  const inputRef = useRef(null);
  const memberId = session.memberId || session.member?.id;
  const roomId = session.roomId || session.id;
  const currentName = session.member?.name || session.name || 'You';

  const load = async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      const [roomData, inboxData, activityData] = await Promise.all([
        api(`/rooms/${encodeURIComponent(roomId)}`, { headers: { 'X-Member-Id': memberId } }),
        memberId ? api(`/rooms/${encodeURIComponent(roomId)}/inbox`, { headers: { 'X-Member-Id': memberId } }) : Promise.resolve([]),
        memberId ? api(`/rooms/${encodeURIComponent(roomId)}/activity`, { headers: { 'X-Member-Id': memberId } }) : Promise.resolve([])
      ]);
      setRoom((previous) => ({ ...previous, ...roomData }));
      setMembers(roomData.members || []);
      setInbox(Array.isArray(inboxData) ? inboxData : (inboxData.files || inboxData.inbox || []));
      setActivity(Array.isArray(activityData) ? activityData : (activityData.activity || []));
      setError('');
    } catch (err) { if (!quiet) setError(err.message); } finally { setLoading(false); }
  };
  useEffect(() => { load(); const timer = setInterval(() => load(true), 12000); return () => clearInterval(timer); }, [roomId, memberId]);
  const availableMembers = members.filter((member) => String(member.id || member.memberId) !== String(memberId));
  const pendingCount = inbox.filter((file) => !file.downloaded && !file.read).length;
  const addFiles = (selected) => {
    const list = Array.from(selected || []);
    if (!recipient) { notify('Choose a recipient first', 'error'); return; }
    setFiles((previous) => [...previous, ...list.filter((file) => !previous.some((item) => item.name === file.name && item.size === file.size))]);
  };
  const upload = async () => {
    if (!files.length || !recipient) return;
    const queue = files.map((file) => ({ id: `${file.name}-${file.lastModified}`, name: file.name, size: file.size, progress: 0, status: 'uploading' }));
    setUploads((previous) => [...queue, ...previous]); setFiles([]);
    await Promise.all(queue.map(async (item, index) => {
      const source = Array.from(inputRef.current?.files || []).find((file) => `${file.name}-${file.lastModified}` === item.id) || null;
      const file = source || files[index];
      try {
        const body = new FormData(); body.append('file', file);
        const result = await new Promise((resolve, reject) => {
          const request = new XMLHttpRequest();
          request.open('POST', `${API_URL}/api/rooms/${encodeURIComponent(roomId)}/transfers?recipientId=${encodeURIComponent(recipient.id || recipient.memberId)}`);
          request.setRequestHeader('X-Member-Id', memberId);
          request.upload.onprogress = (event) => {
            if (event.lengthComputable) {
              setUploads((previous) => previous.map((entry) => entry.id === item.id ? { ...entry, progress: Math.round(event.loaded / event.total * 100) } : entry));
            }
          };
          request.onload = () => {
            let payload = {};
            try { payload = JSON.parse(request.responseText || '{}'); } catch { /* server may return an empty success response */ }
            if (request.status >= 200 && request.status < 300) resolve(payload);
            else reject(new Error(payload.message || payload.error || `Upload failed (${request.status})`));
          };
          request.onerror = () => reject(new Error('Network error while uploading'));
          request.send(body);
        });
        setUploads((previous) => previous.map((entry) => entry.id === item.id ? { ...entry, progress: 100, status: 'done', result } : entry));
      } catch (err) {
        setUploads((previous) => previous.map((entry) => entry.id === item.id ? { ...entry, status: 'error', error: err.message } : entry));
      }
    }));
    notify('Upload queue finished'); load(true);
  };
  const leave = async (close = false) => {
    try {
      await api(`/rooms/${encodeURIComponent(roomId)}/${close ? 'close' : 'leave'}`, { method: 'POST', headers: { 'X-Member-Id': memberId } });
      onLeave();
    } catch (err) { notify(err.message, 'error'); }
  };
  const download = async (file) => {
    try {
      const response = await fetch(`${API_URL}/api/rooms/${encodeURIComponent(roomId)}/transfers/${encodeURIComponent(file.id || file.fileId)}/download`, { headers: { 'X-Member-Id': memberId } });
      if (!response.ok) throw new Error('Download failed');
      const blob = await response.blob(); const url = URL.createObjectURL(blob); const anchor = document.createElement('a');
      anchor.href = url; anchor.download = file.name || 'shared-file'; anchor.click(); URL.revokeObjectURL(url);
      notify('Download started'); load(true);
    } catch (err) { notify(err.message, 'error'); }
  };
  const copyCode = () => { navigator.clipboard?.writeText(room.code || room.roomCode || room.roomId || roomId); notify('Room code copied'); };
  return <div className="app-shell">
    <header className="app-header"><div className="brand"><span className="brand-mark"><Link2 size={18} /></span><span>share<span>room</span></span></div><div className="room-pill"><span>ROOM</span><strong>{room.code || room.roomCode || roomId}</strong><button onClick={copyCode} aria-label="Copy room code"><Copy size={14} /></button></div><div className="header-actions"><button className="icon-button mobile-only" onClick={() => setMobileMenu(!mobileMenu)}><Menu size={20} /></button><Avatar name={currentName} online /><span className="user-name">{currentName}</span><button className="ghost-button" onClick={() => leave(false)}><LogOut size={16} /> Leave</button></div></header>
    <div className="layout">
      <aside className={`sidebar ${mobileMenu ? 'open' : ''}`}><div className="side-room"><span className="mini-kicker">YOUR ROOM</span><div className="side-code">{room.code || room.roomCode || roomId} <button onClick={copyCode}><Copy size={13} /></button></div><p><span className="status-dot" /> Room is active</p></div><nav><button className={activeTab === 'inbox' ? 'selected' : ''} onClick={() => { setActiveTab('inbox'); setMobileMenu(false); }}><Inbox size={18} /> Inbox {pendingCount > 0 && <b>{pendingCount}</b>}</button><button className={activeTab === 'activity' ? 'selected' : ''} onClick={() => { setActiveTab('activity'); setMobileMenu(false); }}><Clock3 size={18} /> Activity</button></nav><div className="sidebar-bottom"><div className="member-count"><Users size={17} /><span><strong>{members.length || 0}</strong> people here</span></div><button className="close-room" onClick={() => leave(true)}><X size={15} /> Close room</button></div></aside>
      {mobileMenu && <div className="mobile-backdrop" onClick={() => setMobileMenu(false)} />}
      <main className="dashboard">
        <div className="dashboard-heading"><div><div className="eyebrow">WELCOME BACK, {currentName.split(' ')[0].toUpperCase()}</div><h1>{activeTab === 'inbox' ? 'Your shared space' : 'Room activity'}</h1><p>{activeTab === 'inbox' ? 'Send something new or see what your team shared with you.' : 'A timeline of everything shared in this room.'}</p></div><button className="refresh-button" onClick={() => load()}><RefreshCw size={16} /> Refresh</button></div>
        {error && <div className="error-banner"><CircleAlert size={17} /> {error}<button onClick={() => load()}><RefreshCw size={15} /></button></div>}
        {activeTab === 'activity' ? <Activity activity={activity} loading={loading} /> : <><section className="send-grid"><div className="panel upload-panel"><div className="panel-title"><div><span className="mini-kicker">SEND A FILE</span><h2>Drop it like it’s hot <span>↘</span></h2></div><span className="panel-badge"><Zap size={14} /> Fast & private</span></div><div className={`dropzone ${dragging ? 'dragging' : ''}`} onDragOver={(e) => { e.preventDefault(); setDragging(true); }} onDragLeave={() => setDragging(false)} onDrop={(e) => { e.preventDefault(); setDragging(false); addFiles(e.dataTransfer.files); }} onClick={() => inputRef.current?.click()}><input ref={inputRef} type="file" multiple hidden onChange={(e) => addFiles(e.target.files)} /><span className="upload-icon"><UploadCloud size={25} /></span><strong>Drop files here, or <u>browse</u></strong><small>Up to 25 MB per file</small></div>{files.length > 0 && <div className="file-queue">{files.map((file) => <div className="queued-file" key={`${file.name}-${file.lastModified}`}><FileText size={17} /><span>{file.name}<small>{formatBytes(file.size)}</small></span><button onClick={() => setFiles(files.filter((item) => item !== file))}><X size={15} /></button></div>)}</div>}<div className="recipient-row"><div className="select-wrap"><label>Send to</label><select value={recipient?.id || recipient?.memberId || ''} onChange={(e) => setRecipient(availableMembers.find((member) => String(member.id || member.memberId) === e.target.value) || null)}><option value="">Choose a person…</option>{availableMembers.map((member) => <option key={member.id || member.memberId} value={member.id || member.memberId}>{member.name || member.displayName}</option>)}</select><ChevronDown size={16} /></div><button className="primary-button send-button" onClick={upload} disabled={!files.length || !recipient}><Send size={16} /> Send {files.length ? `(${files.length})` : ''}</button></div></div><Members members={members} currentId={memberId} selected={recipient} onSelect={setRecipient} /></section><section className="panel files-panel"><div className="panel-title"><div><span className="mini-kicker">YOUR FILES</span><h2>{pendingCount ? `You have ${pendingCount} new file${pendingCount > 1 ? 's' : ''}` : 'Recently shared with you'}</h2></div><button className="text-button" onClick={() => load()}>View all <ArrowRight size={15} /></button></div>{loading ? <LoadingRows /> : inbox.length ? <div className="file-list">{inbox.map((file) => <FileRow file={file} key={file.id || file.fileId || file.name} onDownload={() => download(file)} />)}</div> : <EmptyState icon={Inbox} title="Your inbox is quiet" text="Files shared with you will show up here." />}</section><AnimatePresence>{uploads.length > 0 && <section className="panel upload-status"><div className="panel-title"><div><span className="mini-kicker">TRANSFER STATUS</span><h2>Recent uploads</h2></div><button className="icon-button" onClick={() => setUploads([])}><X size={16} /></button></div>{uploads.map((file) => <div className="transfer-row" key={file.id}><div className="transfer-file"><FileText size={17} /><span>{file.name}<small>{formatBytes(file.size)}</small></span></div><div className={`transfer-state ${file.status}`}>{file.status === 'uploading' ? <><span className="progress"><i style={{ width: `${file.progress}%` }} /></span> Sending…</> : file.status === 'done' ? <><Check size={15} /> Sent</> : <><CircleAlert size={15} /> Failed</>}</div></div>)}</section>}</AnimatePresence></>}</main>
    </div><Toasts items={[]} remove={() => {}} />
  </div>;
}

function Members({ members, currentId, selected, onSelect }) {
  return <div className="panel members-panel"><div className="panel-title"><div><span className="mini-kicker">IN THIS ROOM</span><h2>People <span className="count-chip">{members.length}</span></h2></div><button className="icon-button"><MoreHorizontal size={18} /></button></div><div className="member-list">{members.length ? members.map((member) => { const id = member.id || member.memberId; const isCurrent = String(id) === String(currentId); return <button className={`member ${String(id) === String(selected?.id || selected?.memberId) ? 'chosen' : ''}`} key={id || member.name} onClick={() => !isCurrent && onSelect(member)} disabled={isCurrent}><Avatar name={member.name || member.displayName} online={member.online !== false} /><span><strong>{member.name || member.displayName}</strong><small>{isCurrent ? 'That’s you' : member.online === false ? 'Away' : 'Available'}</small></span>{!isCurrent && <span className="member-select">{String(id) === String(selected?.id || selected?.memberId) ? <Check size={15} /> : <ArrowRight size={15} />}</span>}</button>; }) : <p className="muted">No other members yet.</p>}</div><div className="invite-tip"><MessageCircle size={16} /><span><strong>Invite someone</strong><small>Share the room code to get started</small></span></div></div>;
}

function FileRow({ file, onDownload }) {
  const name = file.fileName || file.name || 'Untitled file'; const Icon = fileIcon(name); const expiry = file.expiresAt ? `Expires ${new Date(file.expiresAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}` : 'Temporary file'; return <motion.div className="file-row" initial={{ opacity: 0 }} animate={{ opacity: 1 }}><span className="file-type"><Icon size={20} /></span><span className="file-meta"><strong>{name}</strong><small>{formatBytes(file.size)} · {file.sender ? `From ${file.sender}` : 'Shared with you'} · {expiry}</small></span><span className="file-date">{file.checksumStatus || 'Verified'}</span><button className="download-button" onClick={onDownload}><Download size={16} /> <span>Download</span></button></motion.div>;
}
function LoadingRows() { return <div className="loading-list">{[1, 2, 3].map((item) => <div className="skeleton-row" key={item}><i /><span /><b /></div>)}</div>; }
function Activity({ activity, loading }) { return <section className="panel activity-panel">{loading ? <LoadingRows /> : activity.length ? <div className="activity-list">{activity.map((item, index) => <div className="activity-item" key={item.id || index}><span className={`activity-icon ${item.type === 'received' ? 'received' : ''}`}>{item.type === 'received' ? <ArrowDownToLine size={17} /> : <Send size={17} />}</span><span><strong>{item.message || `${item.type === 'received' ? 'Received' : 'Sent'} ${item.fileName || 'a file'}`}</strong><small>{item.createdAt ? new Date(item.createdAt).toLocaleString() : 'Recently'}</small></span></div>)}</div> : <EmptyState icon={Clock3} title="No activity yet" text="Your room activity will appear here." />}</section>; }

function App() {
  const [session, setSession] = useState(() => { try { return JSON.parse(localStorage.getItem(sessionKey)); } catch { return null; } });
  const [toasts, setToasts] = useState([]);
  const notify = (message, type = 'success') => { const id = Date.now(); setToasts((items) => [...items, { id, message, type }]); setTimeout(() => setToasts((items) => items.filter((item) => item.id !== id)), 4500); };
  const start = (data) => { setSession(data); localStorage.setItem(sessionKey, JSON.stringify(data)); };
  const leave = () => { setSession(null); localStorage.removeItem(sessionKey); };
  return <>{session ? <Dashboard session={session} onLeave={leave} notify={notify} /> : <Landing onStarted={start} notify={notify} />}<Toasts items={toasts} remove={(id) => setToasts((items) => items.filter((item) => item.id !== id))} /></>;
}

createRoot(document.getElementById('root')).render(<App />);
