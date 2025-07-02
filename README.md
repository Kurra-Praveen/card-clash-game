# card-clash-game

A multiplayer card game where players compete using cricket player statistics. Built with Node.js backend and Android client. 

## How It Works

Card Clash is a turn-based multiplayer card game where players compete using cricket player statistics. Here's how the game works:

1. **Game Setup**
   - 2-6 players can join a game room using a unique room code
   - Each player receives 5 cricket player cards with various statistics
   - Players compete in rounds using their cards' statistics

2. **Gameplay Mechanics**
   - On their turn, players select a card and choose a statistic to compete with
   - Available statistics include:
     - Runs scored
     - Wickets taken
     - Batting average
     - Strike rate
     - Matches played
     - Centuries
     - Five-wicket hauls
     - Economy rate
   - The player with the highest stat value wins the round
   - Winner collects all played cards and scores a point

3. **Game Interface**
   ![Game Interface](mockups/game_interface.png)

## Project Structure

- `client/` - Android client application built with Kotlin and Jetpack Compose
- `server/` - Node.js/Express backend server with Socket.IO for real-time gameplay

## Prerequisites

### Server
- Node.js
- MongoDB
- Environment variables configured in `.env` file

### Client
- Android Studio
- JDK 11
- Android SDK (min SDK 26)

## Setup

### Server Setup
1. Navigate to server directory:
```bash
cd server
npm install
```

2. Configure MongoDB connection in `.env` file

3. Start the server:
```bash
node index1.js
```

### Client Setup
1. Open the `client` directory in Android Studio
2. Sync Gradle files
3. Build and run the application
