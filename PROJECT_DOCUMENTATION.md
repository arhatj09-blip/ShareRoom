# ShareRoom - Complete Project Documentation

## 1. Project identity

**Project name:** ShareRoom  
**Project type:** Room-based remote file-sharing web application  
**Academic context:** Computer Networks mini-project  
**Primary deployment:** React frontend on Vercel and Spring Boot backend on Render  
**Current repository:** `https://github.com/arhatj09-blip/ShareRoom`

ShareRoom allows people to create or join a temporary room, see the other members of that room, choose a private recipient, upload a file, and download files sent to them. The file is transferred through a central backend server rather than directly between browsers.

The project intentionally uses display names instead of full user accounts. It is designed to demonstrate networking, HTTP, REST APIs, file handling, validation, checksums, temporary storage, and deployment concepts without adding enterprise authentication or cloud storage complexity.

---

## 2. Problem statement

Traditional file sharing often requires one of the following:

- Email or messaging attachments
- Cloud storage accounts
- USB drives
- Direct LAN connectivity
- Peer-to-peer browser technologies

Those options may be inconvenient for a small group that wants to share files temporarily. ShareRoom provides a simple room-based workflow:

1. A user creates a room.
2. Other users join using the Room ID.
3. Members see only users in that room.
4. A sender selects a recipient and uploads a file.
5. The backend stores the file temporarily.
6. The recipient sees the file in an inbox and downloads it.

Because both browsers communicate with the public backend, the users do not need to be on the same network.

---

## 3. Core design decision

The project uses a central client-server model:

```text
Sender browser
      |
      | HTTPS REST / multipart upload
      v
Spring Boot server
      |
      +-- SQLite metadata
      +-- temporary uploads directory
      |
      v
Recipient browser downloads the file
```

The project does **not** use:

- WebRTC
- Peer-to-peer transfer
- Direct browser-to-browser sockets
- Custom UDP in the web application
- Cloud object storage
- WebSocket/STOMP
- Password accounts
- OAuth
- Payment systems
- Email verification

The web application uses HTTP REST requests and polling. The older standalone Java examples in `src/` demonstrate direct LAN TCP/UDP techniques separately.

---

## 4. High-level architecture

### 4.1 Production architecture

```text
                              Public internet

  User A browser                                      User B browser
        |                                                    |
        | HTTPS                                              | HTTPS
        +---------------------> Vercel <--------------------+
                                      |
                                      | REST API and multipart upload
                                      v
                         Render Spring Boot service
                                      |
                         +------------+------------+
                         |                         |
                    SQLite database          Persistent disk
                                               /var/data/uploads
```

### 4.2 Deployed components

| Component | Technology | Deployment |
|---|---|---|
| User interface | React and Vite | Vercel |
| Styling | CSS with Tailwind-compatible setup | Bundled into Vercel frontend |
| Animation | Framer Motion | Browser |
| Icons | Lucide React | Browser |
| REST API | Spring Boot | Render |
| Persistence | SQLite and Spring Data JPA | Render persistent disk |
| File bytes | Local `uploads/` directory | Render persistent disk |
| Real-time refresh | HTTP polling | Browser requests every 12 seconds |

### 4.3 Why different networks work

The browsers do not need to know each other's private IP addresses. Each browser makes an outbound request to the public backend. The backend knows the room, sender, recipient, and stored file. NAT therefore does not prevent the transfer in the way it would prevent a direct inbound connection to a private computer.

---

## 5. Repository structure

```text
ShareRoom/
├── .gitignore
├── README.md
├── PROJECT_DOCUMENTATION.md
├── render.yaml
├── backend/
│   ├── .env.example
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       └── main/
│           ├── java/com/shareroom/
│           │   ├── ShareRoomApplication.java
│           │   ├── config/
│           │   │   └── WebConfig.java
│           │   ├── controller/
│           │   │   ├── RoomController.java
│           │   │   └── TransferController.java
│           │   ├── dto/
│           │   │   └── Dtos.java
│           │   ├── entity/
│           │   │   ├── Member.java
│           │   │   ├── Room.java
│           │   │   └── Transfer.java
│           │   ├── exception/
│           │   │   ├── ApiException.java
│           │   │   └── GlobalExceptionHandler.java
│           │   ├── repository/
│           │   │   ├── MemberRepository.java
│           │   │   ├── RoomRepository.java
│           │   │   └── TransferRepository.java
│           │   └── service/
│           │       └── RoomService.java
│           └── resources/
│               └── application.properties
├── frontend/
│   ├── .env.example
│   ├── index.html
│   ├── package.json
│   ├── package-lock.json
│   ├── postcss.config.js
│   ├── tailwind.config.js
│   ├── vite.config.js
│   └── src/
│       ├── main.jsx
│       └── styles.css
└── src/
    ├── LanFileSharingWebServer.java
    └── LanFileTransferApp.java
```

The `src/` Java files are older standalone implementations kept for CNL comparison and reference. The deployed ShareRoom product uses `frontend/` and `backend/`.

---

## 6. Frontend in detail

### 6.1 Frontend stack

The frontend is a Vite-powered React application.

Important dependencies:

- `react`
- `react-dom`
- `framer-motion`
- `lucide-react`
- `vite`
- `tailwindcss`
- `postcss`
- `autoprefixer`

The primary frontend source is [frontend/src/main.jsx](./frontend/src/main.jsx). The visual system is defined in [frontend/src/styles.css](./frontend/src/styles.css).

### 6.2 Landing page

The landing page is implemented by the `Landing` component.

It provides:

- ShareRoom branding
- Soft blue/purple background treatment
- Create Room and Join Room segmented controls
- Display name field
- Optional room name field during creation
- Room code field during joining
- Loading state while the request is in progress
- Animated room card entrance
- Error toast messages
- Temporary-file information

The user does not need a password or account.

### 6.3 Create Room

The frontend sends:

```http
POST /api/rooms
Content-Type: application/json
```

Example request:

```json
{
  "name": "Arhat",
  "roomName": "Project discussion"
}
```

The backend returns the generated Room ID and the member ID. The frontend stores the session in browser `localStorage` under:

```text
shareroom-session
```

The stored session allows the browser to reload the dashboard without asking the user to enter the room again.

### 6.4 Join Room

The frontend sends:

```http
POST /api/rooms/{roomId}/join
Content-Type: application/json
```

Example request:

```json
{
  "name": "Friend"
}
```

The backend validates that:

- The room exists.
- The room is not closed.
- The display name is valid.
- The display name is not already active in that room.

### 6.5 Dashboard

The dashboard is implemented by the `Dashboard` component.

It includes:

- Current Room ID
- Copy Room ID button
- Current display name
- Leave Room button
- Close Room button
- Inbox tab
- Activity tab
- Member count
- Member list
- Recipient selection
- File upload panel
- Upload progress list
- Inbox file list
- Refresh action
- Loading skeletons
- Error banner
- Empty states
- Mobile navigation drawer

### 6.6 Member list

Members are loaded from the room details response. The current member is excluded from recipient selection. Other members can be selected as private recipients.

Each member displays:

- Initials avatar
- Display name
- Current user indication or availability text
- Selected recipient state

The frontend refreshes room details, members, inbox, and activity every 12 seconds:

```javascript
setInterval(() => load(true), 12000)
```

This is polling rather than a WebSocket connection.

### 6.7 Upload process

The upload panel supports:

- Dragging a file into a drop zone
- Selecting a file with a file picker
- Selecting multiple files for a queue
- Preventing duplicate file entries by name and size
- Choosing a recipient before sending
- Upload progress percentages
- Per-file completed and failed states

Uploads use `XMLHttpRequest` instead of a normal `fetch` request because `XMLHttpRequest.upload.onprogress` provides upload progress events.

The request is:

```http
POST /api/rooms/{roomId}/transfers?recipientId={recipientId}
X-Member-Id: {senderMemberId}
Content-Type: multipart/form-data
```

The multipart field is named:

```text
file
```

The frontend sends queued files concurrently with `Promise.all`.

### 6.8 Inbox

The inbox shows transfers where the current user is the recipient.

Each entry includes:

- Filename
- File size
- Sender name
- Upload time
- Expiry information
- Checksum status
- Download button

The backend currently returns `Verified` as the checksum status for successfully stored transfers.

### 6.9 Download process

The frontend requests:

```http
GET /api/rooms/{roomId}/transfers/{transferId}/download
X-Member-Id: {recipientMemberId}
```

The response is a binary file. The frontend:

1. Converts the response to a `Blob`.
2. Creates an object URL.
3. Creates a temporary anchor element.
4. Starts a browser download.
5. Revokes the object URL.

### 6.10 Activity panel

The activity tab displays room transfer history. It is loaded from the activity endpoint and provides an empty state when no transfer exists.

### 6.11 Motion and accessibility

Framer Motion is used for purposeful transitions:

- Landing card entrance
- Join-room field expansion
- Toast entrance and exit
- File-row appearance

The frontend uses `useReducedMotion` so the motion system can respect a user's reduced-motion preference. The application also uses labels, buttons, and `aria-live` for toast announcements.

---

## 7. Backend in detail

### 7.1 Spring Boot application

The backend entry point is [ShareRoomApplication.java](./backend/src/main/java/com/shareroom/ShareRoomApplication.java).

It uses:

```java
@SpringBootApplication
@EnableScheduling
```

`@EnableScheduling` activates the periodic cleanup task in `RoomService`.

### 7.2 Maven dependencies

The backend is defined in [backend/pom.xml](./backend/pom.xml).

Main dependencies:

- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-validation`
- SQLite JDBC driver
- Hibernate community SQLite dialect
- Spring Boot test starter

Java version:

```text
17
```

Spring Boot version:

```text
3.5.6
```

### 7.3 Configuration

The main backend configuration is [application.properties](./backend/src/main/resources/application.properties):

```properties
spring.datasource.url=jdbc:sqlite:${DB_PATH:shareroom.db}
spring.datasource.driver-class-name=org.sqlite.JDBC
spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.open-in-view=false
spring.servlet.multipart.max-file-size=25MB
spring.servlet.multipart.max-request-size=25MB
shareroom.upload-dir=${UPLOAD_DIR:uploads}
shareroom.cors-origins=${CORS_ORIGINS:http://localhost:5173}
server.port=${PORT:8080}
```

The defaults are suitable for local development. Render overrides them with environment variables and a persistent disk.

### 7.4 CORS

[WebConfig.java](./backend/src/main/java/com/shareroom/config/WebConfig.java) enables CORS for `/api/**`.

Allowed methods:

- `GET`
- `POST`
- `DELETE`
- `OPTIONS`

Allowed headers include:

- `X-Member-Id`
- `Content-Type`
- Other browser-requested headers

The allowed origins come from:

```text
CORS_ORIGINS
```

In production it should be set to the exact Vercel origin.

### 7.5 Controllers

The backend separates room operations and transfer operations.

#### RoomController

[RoomController.java](./backend/src/main/java/com/shareroom/controller/RoomController.java) handles:

- Health check
- Room creation
- Room joining
- Room details
- Room members
- Leaving
- Host-only room closing

#### TransferController

[TransferController.java](./backend/src/main/java/com/shareroom/controller/TransferController.java) handles:

- Multipart upload
- Inbox lookup
- Activity lookup
- Download

Controllers remain thin. They receive HTTP data and delegate business rules to `RoomService`.

### 7.6 DTOs

[Dtos.java](./backend/src/main/java/com/shareroom/dto/Dtos.java) contains immutable Java records:

#### CreateRoomRequest

```text
name: required, maximum 80 characters
roomName: optional, maximum 100 characters
```

#### JoinRequest

```text
name: required, maximum 80 characters
```

#### RoomResponse

Contains:

- Short Room ID
- Optional room name
- Internal room UUID
- Member UUID
- Member name

#### MemberResponse

Contains:

- Member UUID
- Display name
- Join timestamp

#### TransferResponse

Contains:

- Transfer UUID
- Original filename
- File size
- Sender name
- Recipient name
- Creation timestamp
- Expiry timestamp
- Checksum status

### 7.7 Entities and database model

#### Room entity

[Room.java](./backend/src/main/java/com/shareroom/entity/Room.java)

Fields:

| Field | Meaning |
|---|---|
| `id` | Internal UUID |
| `code` | Short public Room ID |
| `name` | Optional room name |
| `hostMemberId` | UUID of the host |
| `createdAt` | Creation timestamp |
| `lastActivityAt` | Last room activity |
| `closed` | Whether the room is closed |

Room IDs are generated from a color prefix and four-digit number, such as:

```text
BLUE-4812
```

The code is stored uniquely.

#### Member entity

[Member.java](./backend/src/main/java/com/shareroom/entity/Member.java)

Fields:

| Field | Meaning |
|---|---|
| `id` | Internal UUID |
| `room` | Owning room |
| `name` | Display name |
| `joinedAt` | Join timestamp |
| `lastSeenAt` | Last validated activity |
| `active` | Whether membership is active |

Leaving a room deactivates a member rather than deleting the row. This preserves transfer relationships.

#### Transfer entity

[Transfer.java](./backend/src/main/java/com/shareroom/entity/Transfer.java)

Fields:

| Field | Meaning |
|---|---|
| `id` | Internal UUID |
| `room` | Owning room |
| `sender` | Sending member |
| `recipient` | Receiving member |
| `originalName` | Safe user-facing filename |
| `storedPath` | Generated server-side filename |
| `size` | File size in bytes |
| `sha256` | Stored checksum |
| `createdAt` | Upload timestamp |
| `expiresAt` | Expiry timestamp |

The file bytes are not stored in SQLite. SQLite stores metadata and the disk stores the file.

### 7.8 Repositories

The repositories extend Spring Data JPA:

- [RoomRepository.java](./backend/src/main/java/com/shareroom/repository/RoomRepository.java)
- [MemberRepository.java](./backend/src/main/java/com/shareroom/repository/MemberRepository.java)
- [TransferRepository.java](./backend/src/main/java/com/shareroom/repository/TransferRepository.java)

They provide methods for:

- Finding rooms by Room ID
- Finding open rooms
- Finding active members
- Checking duplicate display names
- Finding inbox transfers by recipient
- Finding activity by room
- Finding expired transfers

### 7.9 Service layer

[RoomService.java](./backend/src/main/java/com/shareroom/service/RoomService.java) contains the core business logic.

Responsibilities:

- Creating rooms
- Joining rooms
- Validating membership
- Tracking activity
- Leaving rooms
- Closing rooms
- Saving uploads
- Sanitizing filenames
- Validating file types
- Calculating SHA-256
- Serving verified downloads
- Removing expired files
- Closing inactive rooms

### 7.10 Membership validation

Protected requests include:

```http
X-Member-Id: UUID
```

The service checks that:

1. The member UUID exists.
2. The member is active.
3. The member belongs to the requested room.
4. The room is the expected room.

The member and room activity timestamps are updated after validation.

### 7.11 Host authorization

The first member created with a room becomes the host. The host UUID is stored in the room.

Only the host can call:

```http
POST /api/rooms/{roomId}/close
```

Other members receive a forbidden response.

### 7.12 Room ID generation

Room IDs are generated using a color prefix and a four-digit number:

```text
BLUE-0000 through BLUE-9999
LIME-0000 through LIME-9999
GOLD-0000 through GOLD-9999
PINK-0000 through PINK-9999
TEAL-0000 through TEAL-9999
MINT-0000 through MINT-9999
```

The service checks the database for collisions before saving.

### 7.13 Display-name validation

The service trims display names, rejects blank or oversized values, and removes carriage-return and newline characters.

The DTO applies a maximum length of 80 characters. Active duplicate names are rejected case-insensitively within one room.

### 7.14 Filename validation

The service:

1. Requires a non-empty original filename.
2. Extracts only the basename using `Paths.get(...).getFileName()`.
3. Replaces unsafe characters with underscores.
4. Rejects blank names.
5. Rejects names longer than 180 characters.
6. Generates a server-side stored filename using a UUID prefix.

Example stored path:

```text
UUID_report.pdf
```

The file is resolved against the normalized upload directory and checked to ensure it remains inside that directory.

### 7.15 File size and extension rules

The configured maximum is:

```text
25 MB
```

The service rejects empty files and files above the limit.

Allowed extensions include:

```text
pdf, txt, csv,
jpg, jpeg, png, gif, webp,
doc, docx, xls, xlsx, ppt, pptx,
zip, 7z, tar, gz,
mp3, mp4
```

Blocked extensions include:

```text
exe, bat, cmd, msi, jar,
com, scr, sh, ps1, vbs, js, dll
```

The allowed-list check is the controlling rule, so an extension must be explicitly permitted.

### 7.16 Upload and checksum workflow

When an upload arrives:

1. The sender is validated as a member of the room.
2. The recipient is validated as another valid member of the same room.
3. The file is checked for non-empty content.
4. The file size is checked.
5. The filename is sanitized.
6. The extension is checked.
7. A UUID-prefixed target path is created.
8. The multipart file is written to disk.
9. SHA-256 is calculated over the stored bytes.
10. Transfer metadata is saved in SQLite.
11. The transfer response returns metadata to the frontend.

If storing the file fails, the service attempts to delete the partial target and returns an error.

### 7.17 Download and checksum workflow

When a recipient downloads:

1. The requested room is loaded.
2. The member is validated.
3. The transfer is located.
4. The transfer must belong to the requested room.
5. The current member must be the transfer recipient.
6. The transfer must not be expired.
7. The stored file must exist.
8. SHA-256 is recalculated from the stored file.
9. The recalculated hash must match the database hash.
10. The file is returned as an `application/octet-stream` response.

The file is not served if any of these checks fail.

### 7.18 Scheduled cleanup

The service runs cleanup every ten minutes:

```text
0 */10 * * * *
```

Cleanup performs two jobs:

#### Expired files

- Finds transfers whose expiry timestamp is in the past.
- Deletes their stored files.
- Deletes their transfer metadata.

#### Inactive or empty rooms

- Finds open rooms.
- Closes rooms whose last activity is older than 30 minutes.
- Closes rooms with no active members.

The transfer expiry itself is set to 24 hours after upload.

### 7.19 Error handling

[ApiException.java](./backend/src/main/java/com/shareroom/exception/ApiException.java) carries an HTTP status and message.

[GlobalExceptionHandler.java](./backend/src/main/java/com/shareroom/exception/GlobalExceptionHandler.java) maps errors to JSON responses.

Examples:

```json
{
  "error": "Room not found"
}
```

Handled categories include:

- Application/API errors
- Invalid arguments
- Multipart files exceeding the configured limit
- Unexpected server errors

---

## 8. REST API reference

### 8.1 Health check

```http
GET /api/rooms/health
```

Response:

```json
{
  "status": "ok"
}
```

This is used by deployment health checks.

### 8.2 Create a room

```http
POST /api/rooms
Content-Type: application/json
```

Request:

```json
{
  "name": "Arhat",
  "roomName": "CNL project"
}
```

Response shape:

```json
{
  "roomId": "BLUE-4812",
  "roomName": "CNL project",
  "roomInternalId": "UUID",
  "memberId": "UUID",
  "memberName": "Arhat"
}
```

### 8.3 Join a room

```http
POST /api/rooms/BLUE-4812/join
Content-Type: application/json
```

Request:

```json
{
  "name": "Friend"
}
```

### 8.4 Get room details

```http
GET /api/rooms/BLUE-4812
X-Member-Id: UUID
```

Response includes:

- Room ID
- Room name
- Closed state
- Active room members

### 8.5 Get room members

```http
GET /api/rooms/BLUE-4812/members
X-Member-Id: UUID
```

### 8.6 Leave a room

```http
POST /api/rooms/BLUE-4812/leave
X-Member-Id: UUID
```

Response:

```text
204 No Content
```

The member is deactivated.

### 8.7 Close a room

```http
POST /api/rooms/BLUE-4812/close
X-Member-Id: HOST_UUID
```

Only the host may close the room.

### 8.8 Upload a transfer

```http
POST /api/rooms/BLUE-4812/transfers?recipientId=RECIPIENT_UUID
X-Member-Id: SENDER_UUID
Content-Type: multipart/form-data
```

Multipart field:

```text
file
```

### 8.9 Get inbox

```http
GET /api/rooms/BLUE-4812/inbox
X-Member-Id: RECIPIENT_UUID
```

Only files addressed to the current member are returned.

### 8.10 Get activity

```http
GET /api/rooms/BLUE-4812/activity
X-Member-Id: MEMBER_UUID
```

The member must belong to the room. The response contains transfers in the room ordered by newest first.

### 8.11 Download a transfer

```http
GET /api/rooms/BLUE-4812/transfers/TRANSFER_UUID/download
X-Member-Id: RECIPIENT_UUID
```

Only the intended recipient can download the transfer.

---

## 9. Complete user workflow

### 9.1 Creator workflow

1. Open the public Vercel URL.
2. Enter a display name.
3. Optionally enter a room name.
4. Click **Create room**.
5. Receive a short Room ID.
6. Copy the Room ID.
7. Share the Room ID with another person.
8. Wait for the other member to join.
9. Select the recipient.
10. Choose or drop a file.
11. Click **Send**.
12. Observe upload progress.
13. See the transfer in the room activity.

### 9.2 Joining workflow

1. Open the same public Vercel URL.
2. Select **Join existing**.
3. Enter a display name.
4. Enter the Room ID.
5. Click **Join room**.
6. See active members.
7. Select a member as a recipient.
8. Upload a file if desired.
9. Open Inbox to see received files.
10. Click Download.

### 9.3 Leave and close behavior

- Any active member can leave.
- Leaving marks the member inactive.
- The host can close the room.
- Closed rooms reject new joins.
- Inactive or empty rooms are automatically closed by scheduled cleanup.

---

## 10. Running locally

### 10.1 Requirements

- Java Development Kit 17 or newer
- Node.js 18 or newer
- npm
- Maven 3.9 or newer if rebuilding the backend

### 10.2 Backend with Maven

```powershell
cd "D:\CNL miniproject\backend"
mvn spring-boot:run
```

If `mvn` is not recognized on Windows, use the Maven installation's full path:

```powershell
& "C:\path\to\apache-maven\bin\mvn.cmd" spring-boot:run
```

### 10.3 Backend using a built jar

```powershell
cd "D:\CNL miniproject\backend"
java -jar .\target\shareroom-backend-0.0.1-SNAPSHOT.jar
```

### 10.4 Frontend

Open another terminal:

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

### 10.5 Local environment variables

Frontend:

```env
VITE_API_URL=http://localhost:8080/api
```

Backend:

```env
PORT=8080
UPLOAD_DIR=uploads
CORS_ORIGINS=http://localhost:5173
DB_PATH=shareroom.db
```

Spring Boot reads the variables directly from the process environment. The `.env.example` files document the values; PowerShell does not automatically load `.env` files into Java.

To set a value for one PowerShell session:

```powershell
$env:PORT = "8081"
$env:CORS_ORIGINS = "http://localhost:5173"
java -jar .\target\shareroom-backend-0.0.1-SNAPSHOT.jar
```

### 10.6 Local test

1. Start the backend.
2. Start the frontend.
3. Create a room in one browser.
4. Join from another browser window.
5. Select the other member.
6. Upload a permitted file.
7. Download the file from the second browser.

---

## 11. Production deployment

### 11.1 Frontend on Vercel

Configure the Vercel project:

```text
Root directory: frontend
Framework: Vite
Build command: npm run build
Output directory: dist
```

Set:

```text
VITE_API_URL=https://YOUR-RENDER-BACKEND-URL.onrender.com/api
```

Redeploy after changing this variable because Vite embeds it during the build.

### 11.2 Backend on Render

The repository includes [render.yaml](./render.yaml) and [backend/Dockerfile](./backend/Dockerfile).

Render uses:

```text
Docker context: repository root
Dockerfile: backend/Dockerfile
Health check: /api/rooms/health
```

Environment:

```text
PORT=8080
CORS_ORIGINS=https://YOUR-VERCEL-DOMAIN.vercel.app
DB_PATH=/var/data/shareroom.db
UPLOAD_DIR=/var/data/uploads
```

Persistent disk:

```text
Mount path: /var/data
```

The disk is required to preserve:

```text
/var/data/shareroom.db
/var/data/uploads
```

### 11.3 Deployment request path

When a user creates a room in production:

```text
Browser
  -> https://frontend.vercel.app
  -> https://backend.onrender.com/api/rooms
  -> Spring Boot RoomController
  -> RoomService
  -> SQLite
  -> JSON response
```

### 11.4 Common production 404 problem

If the frontend calls:

```text
https://frontend.vercel.app/api/rooms
```

Vercel returns 404 because Vercel is hosting the React files, not Spring Boot.

The correct request must be:

```text
https://backend.onrender.com/api/rooms
```

Fix:

1. Deploy Spring Boot to Render.
2. Set Vercel's `VITE_API_URL`.
3. Redeploy Vercel.
4. Set Render's `CORS_ORIGINS` to the exact Vercel origin.

---

## 12. Security model and limitations

### 12.1 Security measures implemented

- Room membership validation
- Recipient membership validation
- Host-only close operation
- UUID internal identifiers
- Filename basename extraction
- Unsafe filename character replacement
- Path normalization and directory containment check
- Extension allow-list
- Executable extension block-list
- Multipart size limits
- SHA-256 after upload
- SHA-256 before download
- Temporary expiry
- CORS restriction
- No direct public file path exposure

### 12.2 What SHA-256 does and does not do

SHA-256 verifies integrity. It can detect if the stored bytes change unexpectedly.

SHA-256 does not:

- Encrypt the file
- Authenticate a user
- Prove who sent the file
- Replace HTTPS

### 12.3 Authentication limitation

The current application uses display names and Room IDs. It does not have:

- Passwords
- Account registration
- OAuth
- Email verification
- Signed session tokens
- Multi-factor authentication

Anyone who knows a Room ID may attempt to join. This is acceptable for the mini-project but not sufficient for sensitive production data.

### 12.4 Public deployment limitations

Before using the application for real sensitive data, add:

- Strong authentication
- Authorization tokens
- Rate limiting
- Malware scanning
- Content inspection
- Upload quotas
- Database backups
- Managed object storage
- Audit logs
- Better session expiration
- HTTPS enforcement

---

## 13. CNL concepts demonstrated

### 13.1 TCP

HTTP normally runs over TCP. TCP provides:

- Reliable delivery
- Ordered bytes
- Retransmission
- Connection management

The web application relies on the browser and server HTTP stacks to use TCP. The older desktop application separately demonstrates direct TCP sockets.

### 13.2 HTTP

The application uses:

- `GET` for reading data and downloading files
- `POST` for creating rooms, joining, uploading, leaving, and closing
- JSON for structured requests and responses
- Multipart form data for file uploads
- HTTP status codes for success and errors

### 13.3 REST

Resources are represented by URL paths:

- Rooms
- Members
- Transfers

Controllers expose HTTP operations while the service layer owns business rules.

### 13.4 Client-server architecture

The frontend is a thin client that:

- Collects input
- Shows UI state
- Sends requests
- Displays responses

The backend:

- Validates membership
- Creates and closes rooms
- Saves metadata
- Stores files
- Calculates checksums
- Serves authorized downloads

### 13.5 NAT

NAT gives many devices private addresses and usually prevents unsolicited inbound traffic. Direct peer-to-peer file transfer would require special network traversal. ShareRoom avoids that by routing both users through the public backend.

### 13.6 HTTPS

HTTPS encrypts browser-to-server traffic. Vercel and Render provide HTTPS for deployed URLs. Local HTTP should be used only for development or a trusted LAN.

### 13.7 Polling

The dashboard refreshes its data periodically rather than keeping a WebSocket connection. Polling is easier to implement and appropriate for a small mini-project, although WebSockets could reduce latency and unnecessary requests in a larger system.

### 13.8 Storage

SQLite stores metadata. The filesystem stores file bytes. This separation is practical because binary files are easier to stream from disk than to embed in database rows for this project.

---

## 14. Legacy standalone applications

The repository retains two earlier Java examples under `src/`.

### 14.1 LanFileSharingWebServer.java

This is a dependency-free Java built-in `HttpServer` implementation. It:

- Runs on port 8080
- Provides browser pages and API handlers
- Stores files under `shared-files`
- Uses in-memory maps for users, files, and activities
- Has a 25 MB file limit
- Uses allowed and blocked extensions
- Supports expiry cleanup
- Uses SHA-256

It is not the deployed Spring Boot backend.

### 14.2 LanFileTransferApp.java

This is a Swing desktop client/server application. It:

- Uses Java Swing
- Has Send File and Receive Files tabs
- Supports direct LAN TCP transfer
- Supports experimental UDP transfer
- Uses a custom packet format for UDP
- Implements ACK and retry logic
- Supports simulated packet loss
- Uses temporary `.part` files
- Uses SHA-256 verification

It is useful for explaining direct LAN networking and TCP/UDP, but it is not the deployed cross-network ShareRoom web application.

---

## 15. Testing checklist

### Frontend build

```powershell
cd "D:\CNL miniproject\frontend"
npm run build
```

Expected result:

```text
vite ... built successfully
```

### Backend build

```powershell
cd "D:\CNL miniproject\backend"
mvn -DskipTests package
```

Expected output:

```text
BUILD SUCCESS
```

### Functional test

- [ ] Backend health endpoint responds.
- [ ] Frontend opens.
- [ ] Room creation succeeds.
- [ ] Room joining succeeds.
- [ ] Duplicate active names are rejected.
- [ ] Members are isolated by room.
- [ ] Recipient selection works.
- [ ] Allowed file uploads work.
- [ ] Oversized files are rejected.
- [ ] Executable extensions are rejected.
- [ ] Upload progress is visible.
- [ ] Inbox refreshes.
- [ ] Download works for the recipient.
- [ ] Unauthorized download is rejected.
- [ ] Leave works.
- [ ] Host close works.
- [ ] Non-host close is rejected.
- [ ] Expired files are cleaned.
- [ ] Inactive rooms are closed.

### Deployment test

- [ ] Vercel frontend URL opens.
- [ ] Render health endpoint returns `{"status":"ok"}`.
- [ ] Vercel `VITE_API_URL` points to Render.
- [ ] Render `CORS_ORIGINS` points to Vercel.
- [ ] Persistent disk is mounted at `/var/data`.
- [ ] Two users on different networks can join the same room.
- [ ] Cross-network file upload works.
- [ ] Cross-network download works.

---

## 16. Future improvements

Possible improvements include:

- Password-protected rooms
- Signed or expiring member sessions
- WebSocket/STOMP notifications
- Progress events from server to recipient
- Transfer cancellation
- Resumable uploads
- Multiple recipient groups
- Postgres for production metadata
- S3-compatible object storage
- Virus scanning
- Per-user quotas
- File previews
- Searchable activity
- Room passwords
- Invite links
- Better presence tracking
- Deployment-specific monitoring
- Automated backend and frontend tests

---

## 17. Final summary

ShareRoom is a full-stack file-sharing system that demonstrates how a browser client, REST backend, database, and server-side file storage work together.

The most important implementation ideas are:

1. The frontend is deployed separately from the backend.
2. The frontend must use `VITE_API_URL` to reach the Render API.
3. The backend is the central transfer point.
4. Room IDs isolate groups.
5. UUIDs identify internal records.
6. Membership is checked on every protected operation.
7. Files are stored temporarily and expire.
8. SHA-256 verifies stored file integrity.
9. CORS allows the Vercel origin to call Render.
10. Persistent storage is required for SQLite and uploaded files.
11. Polling keeps the room dashboard current.
12. The system works across different networks because it does not depend on direct peer-to-peer connectivity.
