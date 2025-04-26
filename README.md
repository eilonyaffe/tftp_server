# Extended TFTP Server and Client

## Overview
This project implements an **extended version of TFTP (Trivial File Transfer Protocol)** using **Java**.  
It includes:
- A **multi-threaded TCP-based server** following a **Thread-Per-Client (TPC)** model.
- A **client application** that communicates using a **binary protocol** to upload, download, delete, and list files.
- Support for **broadcasting** file additions and deletions to all connected clients.

The original TFTP protocol is UDP-based; this version adapts it to TCP while enhancing functionality such as user login/logout and error handling.

## Features
- **Login (LOGRQ)**: Clients must log in with a unique username.
- **Upload (WRQ)**: Clients can upload files to the server.
- **Download (RRQ)**: Clients can download files from the server.
- **Delete (DELRQ)**: Clients can delete files on the server.
- **Directory Listing (DIRQ)**: Clients can request a list of available files.
- **Disconnect (DISC)**: Clients can log out and close the connection.
- **Broadcast (BCAST)**: Server broadcasts file additions and deletions to all logged-in clients.
- **Error Handling**: Robust handling for login failures, missing files, name conflicts, and more.

## Protocol
All communication between the client and server is **binary encoded** with **Big Endian** formatting.  
Supported packet types:
- `LOGRQ`, `WRQ`, `RRQ`, `DELRQ`, `DIRQ`, `DATA`, `ACK`, `BCAST`, `ERROR`, `DISC`

Each packet begins with a 2-byte opcode followed by type-specific fields.

## Build & Run Instructions

### Prerequisites
- Java 17+
- Maven 3.8+

---

### Build Server
```bash
cd server
mvn compile
