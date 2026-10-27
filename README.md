# ShareRoom

ShareRoom is a room-based remote file-sharing web application for a CNL mini-project. Users open the same website, create or join a short-lived room, select a room member, and send a file privately through a central Spring Boot server.

The current deployment is:

- **Frontend:** Vercel
- **Backend:** Render
- **Database:** SQLite on the backend's persistent disk
- **File storage:** `uploads/` on the backend's persistent disk
- **Updates:** HTTP polling from the React dashboard

There is no WebRTC, peer-to-peer transfer, cloud object storage, password authentication, or WebSocket requirement in this version.

## Live architecture

```text
User A browser                    User B browser
       |                                 |
       | HTTPS                           | HTTPS
       +-------------> Vercel <---------+
                           |
                           | REST and multipart requests
                           v
                    Render Spring Boot API
                           |
                  +--------+--------+
                  |                 |
              SQLite DB       uploads/ files
```

The browser clients never connect directly to each other. This is why users can share files while connected to different networks.

## Repository structure

```text
ShareRoom/
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/shareroom/
│       │   ├── config/
│       │   ├── controller/
│       │   ├── dto/
│       │   ├── entity/
│       │   ├── exception/
│       │   ├── repository/
│       │   └── service/
│       └── resources/application.properties
├── frontend/
│   ├── src/main.jsx
│   ├── src/styles.css
│   ├── package.json
│   └── vite.config.js
├── render.yaml
├── README.md
└── src/
    └── older standalone Java examples
```

## Features

- Create a room with a display name and optional room name.
- Generate short Room IDs such as `BLUE-4812`.
- Join an existing room using its Room ID.
- Host-only Close Room action.
- Leave Room action for all members.
- Room member list with initials and online state.
- Private one-to-one recipient selection.
- Drag-and-drop and file-picker uploads.
- Animated upload progress and transfer status.
- Inbox with sender, size, upload time, expiry, checksum status, and download action.
- Room activity history.
- Polling refresh every 12 seconds.
- Loading skeletons, empty states, error states, and toast notifications.
- Responsive desktop and mobile layouts.
- `prefers-reduced-motion` support.
- 25 MB maximum upload size.
- Allowed document, image, archive, audio, video, and text formats.
- Executable extension blocking.
- Filename sanitization and path traversal protection.
- SHA-256 verification after upload and before download.
- Automatic file expiry after 24 hours.
- Automatic inactive/empty room cleanup after 30 minutes.

## Production deployment

The production frontend is deployed on Vercel and the Spring Boot backend is deployed on Render. The exact public frontend domain can be configured in Vercel; the backend URL is configured through the environment variables below.

### Vercel frontend

Create or connect a Vercel project to this repository. Use the `frontend` directory as the project root:

```text
Root Directory: frontend
Framework preset: Vite
Build command: npm run build
Output directory: dist
```

Set this Vercel environment variable for Production, Preview, and Development as needed:

```text
VITE_API_URL=https://YOUR-RENDER-BACKEND-URL.onrender.com/api
```

After changing it, redeploy Vercel. `VITE_API_URL` is embedded during the Vite build, so changing the variable does not affect an already-built deployment.

### Render backend

This repository includes [render.yaml](./render.yaml) and [backend/Dockerfile](./backend/Dockerfile). The Render service uses:

```text
Docker context: repository root
Dockerfile: backend/Dockerfile
Health check: /api/rooms/health
```

Configure these Render environment variables:

```text
PORT=8080
CORS_ORIGINS=https://YOUR-VERCEL-DOMAIN.vercel.app
DB_PATH=/var/data/shareroom.db
UPLOAD_DIR=/var/data/uploads
```

Add a Render persistent disk mounted at:

```text
/var/data
```

The persistent disk is required because the application stores the SQLite database and uploaded files locally. Without it, a restart or redeploy can remove room metadata and files.

After the backend is deployed, verify:

```text
https://YOUR-RENDER-BACKEND-URL.onrender.com/api/rooms/health
```

Expected response:

```json
{"status":"ok"}
```

Opening the backend root URL itself is also supported and returns a small JSON service description. It is an API service, not the browser interface; use the Vercel URL to open the ShareRoom website.

Then set `VITE_API_URL` in Vercel to the same backend URL with `/api` appended and redeploy the frontend.

### Production troubleshooting

If Create Room or Join Room returns `404`:

1. Open the browser developer tools and inspect the failed request.
2. The request must target:

   ```text
   https://YOUR-RENDER-BACKEND-URL.onrender.com/api/rooms
   ```

3. If it targets the Vercel domain, `VITE_API_URL` is missing or the frontend was not redeployed.
4. If the backend health URL is not returning `{"status":"ok"}`, fix the Render service first.
5. Confirm Render's `CORS_ORIGINS` exactly matches the Vercel origin without a trailing slash.

## Local development

### Requirements

- Node.js 18+ and npm
- JDK 25+
- Maven 3.9+ for rebuilding the backend

### Start the backend

From the project root:

```powershell
cd "D:\CNL miniproject\backend"
mvn spring-boot:run
```

If Maven is not on Windows `PATH`, run it using its full path or run a previously built jar:

```powershell
& "C:\path\to\apache-maven\bin\mvn.cmd" spring-boot:run
```

```powershell
java -jar .\target\shareroom-backend-0.0.1-SNAPSHOT.jar
```

The local API is:

```text
http://localhost:8080
```

### Start the frontend

Open a second terminal:

```powershell
cd "D:\CNL miniproject\frontend"
npm install
Copy-Item .env.example .env
npm run dev
```

Open the Vite URL, normally:

```text
http://localhost:5173
```

The local frontend calls `http://localhost:8080/api` through [frontend/.env.example](./frontend/.env.example).

### Local test flow

1. Open the frontend.
2. Create a room with a display name.
3. Open the frontend again in another browser window.
4. Join using the Room ID.
5. Select the other member.
6. Upload a file smaller than 25 MB.
7. Download it from the recipient's Inbox.

## Backend API

Protected endpoints require the `X-Member-Id` header returned when a user creates or joins a room.

```text
GET    /api/rooms/health
POST   /api/rooms
POST   /api/rooms/{roomId}/join
GET    /api/rooms/{roomId}
GET    /api/rooms/{roomId}/members
POST   /api/rooms/{roomId}/leave
POST   /api/rooms/{roomId}/close

POST   /api/rooms/{roomId}/transfers
GET    /api/rooms/{roomId}/inbox
GET    /api/rooms/{roomId}/activity
GET    /api/rooms/{roomId}/transfers/{transferId}/download
```

The upload endpoint accepts a multipart field named `file` and a `recipientId` query parameter.

## Environment variables

### Frontend

```env
VITE_API_URL=http://localhost:8080/api
```

For Vercel, replace the local value with the public Render API URL.

### Backend

```env
PORT=8080
UPLOAD_DIR=uploads
CORS_ORIGINS=http://localhost:5173
DB_PATH=shareroom.db
```

Environment files are examples only. Do not commit credentials or private deployment values.

## CNL viva notes

### HTTP and TCP

The browser communicates with Spring Boot through HTTP REST requests. Normal HTTP deployment runs over TCP, which provides reliable, ordered delivery. File uploads use HTTP multipart form data. Unlike the older desktop example, this web version does not implement a custom UDP protocol.

### Client-server architecture

React is the client and Spring Boot is the central server. The server owns room membership, authorization checks, metadata, temporary storage, and checksums. Both users connect to the public server, so they do not need to be on the same LAN.

### Room IDs and isolation

Room IDs are short human-friendly lookup codes. Database records use UUIDs internally. Every protected operation validates that the supplied member belongs to the requested room. Transfers also validate the intended recipient.

### NAT

Private networks commonly use NAT, which makes direct inbound connections difficult. This application avoids peer-to-peer networking: both browsers make outbound HTTPS connections to Render, so no port forwarding is required.

### HTTPS

Vercel and Render can provide HTTPS for deployed traffic. HTTPS encrypts the connection. Local development HTTP is not encrypted and should only be used on a trusted machine or LAN.

### Validation and hashing

The backend validates room membership, recipient membership, file size, extension, filename, and storage path. It calculates SHA-256 after upload and recalculates it before download. SHA-256 verifies integrity; it is not encryption and does not authenticate a user.

### Temporary storage

SQLite stores room, member, and transfer metadata. File bytes are stored in the backend `uploads/` directory. A scheduled job deletes expired files after 24 hours and closes inactive or empty rooms after 30 minutes. Render's persistent disk keeps this local state across service restarts.

## Scope and future improvements

This is a college mini-project using display names rather than full authentication. It intentionally does not include payments, email verification, OAuth, cloud object storage, or enterprise identity management. A production system should add stronger authentication, rate limiting, malware scanning, PostgreSQL, managed object storage, and more detailed audit logging.
