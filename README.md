# ShareRoom

ShareRoom is a room-based remote file-sharing web application for a CNL mini-project. A sender uploads a file to the Spring Boot server, the server stores it temporarily, and the intended recipient downloads it from the same web application. There is no WebRTC, peer-to-peer transfer, cloud object storage, or password authentication in this mini-project.

## Architecture

```text
React + Vite browser
        |
        | HTTP REST / multipart upload
        v
Spring Boot REST API
        |
        +-- SQLite metadata (rooms, members, transfers)
        +-- uploads/ temporary file storage
```

The frontend is in `frontend/`; the backend is in `backend/`. The older standalone Java examples remain under `src/` for reference.

## Requirements

- Node.js 18+ and npm
- JDK 17+
- Maven 3.9+ (or use the already-built jar below)

## Local setup

### Backend

If Maven is installed and available on `PATH`:

```powershell
cd "D:\CNL miniproject\backend"
mvn spring-boot:run
```

If PowerShell says `mvn is not recognized`, use the already-built jar. This does not require Maven:

```powershell
cd "D:\CNL miniproject\backend"
java -jar .\target\shareroom-backend-0.0.1-SNAPSHOT.jar
```

Alternatively, use the Maven installation available in this workspace:

```powershell
cd "D:\CNL miniproject\backend"
& "C:\Users\Arhat\.copilot\maven\apache-maven-3.9.9\bin\mvn.cmd" spring-boot:run
```

The API starts on `http://localhost:8080` by default. SQLite metadata is stored in `backend/shareroom.db`; uploaded files are stored in `backend/uploads/`.

### Frontend

Open a second PowerShell window:

```powershell
cd "D:\CNL miniproject\frontend"
Copy-Item .env.example .env
npm install
npm run dev
```

Open the Vite URL shown in the terminal, normally `http://localhost:5173`.

The frontend reads `VITE_API_URL` from `.env`. For a deployed frontend, set it to the public API URL, for example `https://api.example.com/api`.

## LAN testing

Run the backend on a machine reachable by the other devices, set `CORS_ORIGINS` to the frontend origin, and start Vite with a host binding if needed:

```powershell
npm run dev -- --host 0.0.0.0
```

Use the host machine's LAN address from other devices. Open the same frontend URL, create a room on one browser, and join it from another browser with the displayed Room ID. Local HTTP is not encrypted; use it only on a trusted network.

## Exact first-run checklist

1. Open PowerShell window 1 and verify Java:

   ```powershell
   java -version
   ```

2. Start the backend without Maven:

   ```powershell
   cd "D:\CNL miniproject\backend"
   java -jar .\target\shareroom-backend-0.0.1-SNAPSHOT.jar
   ```

   Leave this window running. Successful startup should expose the API at `http://localhost:8080`.

3. Open PowerShell window 2 and start the frontend:

   ```powershell
   cd "D:\CNL miniproject\frontend"
   npm run dev
   ```

4. Open the URL printed by Vite, normally `http://localhost:5173`.

5. Click **Create new**, enter a display name, and create a room.

6. Open the same frontend URL in another browser window or another computer, choose **Join existing**, and enter the Room ID.

7. Select the other member, choose a file, and click **Send**.

8. Download the file from the recipient's Inbox.

9. Stop each process with `Ctrl+C` in its corresponding PowerShell window.

The `.env.example` files document the supported environment variables. PowerShell does not automatically load a `.env` file into a Java process, so for local defaults you can leave the files unchanged; Spring Boot uses the defaults in `application.properties`. To override a backend setting for one PowerShell session, use for example:

```powershell
$env:PORT = "8081"
$env:CORS_ORIGINS = "http://localhost:5173"
java -jar .\target\shareroom-backend-0.0.1-SNAPSHOT.jar
```

## Product flow

1. A user creates a room with a display name. ShareRoom generates a short ID such as `BLUE-4812`.
2. Other users join with the Room ID and a display name.
3. Members see only users in their room.
4. A member selects a recipient and uploads one allowed file.
5. The backend validates membership, filename, extension, size, and recipient; then stores the file and checksum.
6. The recipient's inbox refreshes through polling and provides a download action.
7. Downloads are revalidated against room membership and the stored SHA-256 checksum.
8. Files expire after 24 hours. Empty or inactive rooms are cleaned after 30 minutes.

## File rules

- Maximum file size: 25 MB.
- Common document, image, archive, and text formats are allowed.
- Executable formats including `.exe`, `.bat`, `.cmd`, `.msi`, and `.jar` are rejected.
- Filenames are reduced to a safe basename and unsafe characters are rejected or normalized.
- Files are written under `uploads/`, never directly from an untrusted path.

## Deployment

Build the backend with `mvn clean package` and run the generated jar. Build the frontend with `npm run build` and serve `frontend/dist` from a static host. Configure `VITE_API_URL` to the public backend URL and configure the backend CORS origin to the deployed frontend origin. The deployment platform or a reverse proxy should terminate HTTPS. Do not expose plain local-development HTTP to the public internet.

For production, use a persistent database, private storage, stronger authentication, rate limiting, antivirus scanning, and a managed object store. Those are intentionally outside this college mini-project's scope.

## CNL viva notes

### TCP and HTTP

HTTP is an application-layer protocol carried over TCP in the normal deployment. The browser sends REST requests and a multipart file body; Spring Boot parses and validates it. TCP provides ordered, reliable delivery between browser and server. Unlike the older desktop demo, this version does not implement a custom UDP protocol.

### Client-server architecture

The React application is the client. Spring Boot is the central server. The server owns room membership, authorization checks, metadata, temporary storage, and checksums, so users can connect from different networks after deployment.

### Room IDs

The short Room ID is a human-friendly lookup key. Internal database records use UUIDs. Every protected operation also carries a member identity and checks that the member belongs to the requested room, which isolates files between rooms.

### NAT

On a private LAN, NAT gives devices private addresses and usually prevents unsolicited inbound traffic. A public deployment avoids requiring users to port-forward: every browser makes an outbound HTTPS connection to the deployed server. A reverse proxy or hosting platform can provide the public address.

### HTTPS

Plain HTTP exposes metadata and file contents to network observers. HTTPS encrypts the connection and should be provided by the deployment platform or reverse proxy. SHA-256 checks integrity but is not encryption or authentication.

### Validation and hashing

The server validates room membership, recipient membership, content length, extension, safe filename, and storage path. It calculates SHA-256 after upload and again before download so corrupted or unexpectedly modified files are not served as verified files.

### Temporary storage

Metadata is stored in SQLite and file bytes are stored in `uploads/`. A scheduled cleanup removes expired files after 24 hours and inactive/empty rooms after 30 minutes. This keeps the mini-project simple while making its retention behavior explicit.
