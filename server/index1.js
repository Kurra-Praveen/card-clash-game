const express = require('express');
const { Server } = require('socket.io');
const { MongoClient } = require('mongodb');
const crypto = require('crypto');
const winston = require('winston');
require('dotenv').config();
const app = express();
const server = require('http').createServer(app);
const io = new Server(server, { cors: { origin: '*' } });
app.use(express.json());

const logger = winston.createLogger({
    level: 'info',
    format: winston.format.combine(
        winston.format.timestamp(),
        winston.format.json()
    ),
    transports: [
        new winston.transports.Console(),
        new winston.transports.File({ filename: 'server.log' })
    ]
});

const uri = process.env.MONGODB_URI;
const client = new MongoClient(uri);
let cardsCollection;

async function connectDB() {
    try {
        await client.connect();
        cardsCollection = client.db('test').collection('cards');
        logger.info('Connected to MongoDB');
        const count = await cardsCollection.countDocuments();
        logger.info(`Initial card count: ${count}`);
    } catch (error) {
        logger.error('MongoDB connection failed:', error);
    }
}
connectDB();

const rooms = new Map();
const statWeights = {
    runs: 1.0,
    wickets: 1.0,
    battingAverage: 1.5,
    strikeRate: 1.2,
    matchesPlayed: 0.8,
    centuries: 2.0,
    fiveWicketHauls: 2.5
};

function generateRoomCode() {
    return crypto.randomBytes(3).toString('hex').toUpperCase();
}

app.post('/create-room', (req, res) => {
    const { maxPlayers } = req.body;
    if (!maxPlayers || maxPlayers < 2 || maxPlayers > 6) {
        logger.error(`Invalid maxPlayers: ${maxPlayers}`);
        return res.status(400).json({ error: 'Max players must be 2-6' });
    }
    const roomCode = generateRoomCode();
    rooms.set(roomCode, {
        maxPlayers,
        playerCount: 1,
        sockets: [`mock_${crypto.randomBytes(4).toString('hex')}`],
        gameState: null,
        submissions: new Map(),
        scores: new Map(),
        challengeResponses: new Map(),
        activeStat: null
    });
    logger.info(`Room created: ${roomCode}, maxPlayers: ${maxPlayers}`);
    res.json({ roomCode, playerCount: 1, maxPlayers });
});

app.post('/join-room', (req, res) => {
    const { roomCode } = req.body;
    const normalizedCode = roomCode.toUpperCase();
    const room = rooms.get(normalizedCode);
    if (!room) {
        logger.error(`Join room failed: Room ${normalizedCode} not found`);
        return res.status(404).json({ error: 'Room not found' });
    }
    if (room.playerCount >= room.maxPlayers) {
        logger.error(`Join room failed: Room ${normalizedCode} is full`);
        return res.status(400).json({ error: 'Room is full' });
    }
    room.playerCount++;
    room.sockets.push(`mock_${crypto.randomBytes(4).toString('hex')}`);
    logger.info(`Player joined room: ${normalizedCode}, playerCount: ${room.playerCount}, sockets: ${room.sockets.length}`);
    res.json({ roomCode: normalizedCode, playerCount: room.playerCount, maxPlayers: room.maxPlayers });
    if (room.playerCount === room.maxPlayers) {
        startCountdown(normalizedCode);
    }
});

app.get('/get-cards', async (req, res) => {
    try {
        const cards = await cardsCollection.find({}).toArray();
        logger.info(`Retrieved ${cards.length} cards from database`);
        res.json({ cards, count: cards.length });
    } catch (error) {
        logger.error(`Get cards failed: ${error.message}`);
        res.status(500).json({ error: 'Failed to retrieve cards' });
    }
});

app.post('/start-game', async (req, res) => {
    const { roomCode } = req.body;
    const normalizedCode = roomCode.toUpperCase();
    const room = rooms.get(normalizedCode);
    if (!room) {
        logger.error(`Start game failed: Room ${normalizedCode} not found`);
        return res.status(404).json({ error: 'Room not found' });
    }
    try {
        const cardCount = await cardsCollection.countDocuments();
        if (cardCount < room.maxPlayers * 5) {
            logger.error(`Insufficient cards: need ${room.maxPlayers * 5}, got ${cardCount}`);
            return res.status(500).json({ error: 'Insufficient cards' });
        }
        const cards = await cardsCollection.aggregate([{ $sample: { size: room.maxPlayers * 5 } }]).toArray();
        const shuffledCards = cards.sort(() => Math.random() - 0.5);
        room.gameState = {
            players: room.sockets.map((socketId, index) => ({
                socketId,
                cardCount: 5,
                cards: shuffledCards.slice(index * 5, (index + 1) * 5)
            })),
            currentTurn: room.sockets[0],
            round: 1
        };
        room.scores = new Map(room.sockets.map(socketId => [socketId, 0]));
        logger.info(`Game initialized for room: ${normalizedCode}, players: ${room.gameState.players.length}`);
        room.sockets.forEach((socketId, index) => {
            if (!socketId.startsWith('mock_')) {
                const playerCards = room.gameState.players[index].cards;
                io.to(socketId).emit('gameData', {
                    players: room.gameState.players.map(p => ({
                        socketId: p.socketId,
                        cardCount: p.cardCount
                    })),
                    currentTurn: room.gameState.currentTurn,
                    round: room.gameState.round,
                    cards: playerCards,
                    scores: Object.fromEntries(room.scores)
                });
                logger.info(`Sent gameData to socket ${socketId} in room ${normalizedCode}`);
            }
        });
        res.json({ message: 'Game started' });
    } catch (error) {
        logger.error(`Start game error for room ${normalizedCode}: ${error.message}`);
        res.status(500).json({ error: 'Failed to start game' });
    }
});

function startCountdown(roomCode) {
    let countdown = 3;
    const interval = setInterval(() => {
        io.to(roomCode).emit('countdown', countdown);
        logger.info(`Countdown for ${roomCode}: ${countdown}`);
        countdown--;
        if (countdown < 0) {
            clearInterval(interval);
            io.to(roomCode).emit('gameStart');
            logger.info(`Game started for room: ${roomCode}`);
        }
    }, 1000);
}

function joinRoom(socket, roomCode) {
    const normalizedCode = roomCode.toUpperCase();
    const room = rooms.get(normalizedCode);
    if (!room) {
        logger.error(`Socket ${socket.id} failed to join room ${normalizedCode}: Room not found`);
        return socket.emit('error', 'Room not found');
    }
    if (room.playerCount > room.maxPlayers) {
        logger.error(`Socket ${socket.id} failed to join room ${normalizedCode}: Room is full`);
        return socket.emit('error', 'Room is full');
    }
    const mockSocketIndex = room.sockets.findIndex(s => s.startsWith('mock_'));
    if (mockSocketIndex !== -1) {
        room.sockets[mockSocketIndex] = socket.id;
    } else {
        room.sockets.push(socket.id);
    }
    socket.join(normalizedCode);
    logger.info(`Socket ${socket.id} joined room ${normalizedCode}, sockets: ${room.sockets.length}`);
}

io.on('connection', (socket) => {
    logger.info(`New socket connection: ${socket.id}`);
    socket.on('joinRoom', (roomCode) => joinRoom(socket, roomCode));
    // Existing imports and setup remain unchanged
     // Only replace the submitStat handler within io.on('connection', ...)
     socket.on('submitStat', ({ roomCode, cardIndex, stat, value }) => {
         const normalizedCode = roomCode.toUpperCase();
         const room = rooms.get(normalizedCode);
         if (!room || !room.gameState) {
             logger.error(`Submit stat failed for socket ${socket.id}: Room ${normalizedCode} not found or game not started`);
             return socket.emit('error', 'Room not found or game not started');
         }
         if (room.gameState.currentTurn !== socket.id) {
             logger.error(`Submit stat failed for socket ${socket.id}: Not your turn`);
             return socket.emit('error', 'Not your turn');
         }
         const player = room.gameState.players.find(p => p.socketId === socket.id);
         if (cardIndex < 0 || cardIndex >= player.cards.length) {
             logger.error(`Invalid cardIndex ${cardIndex} for socket ${socket.id}`);
             return socket.emit('error', 'Invalid card index');
         }
         if (!statWeights.hasOwnProperty(stat)) {
             logger.error(`Invalid stat ${stat} for socket ${socket.id}`);
             return socket.emit('error', 'Invalid stat');
         }
         if (room.activeStat) {
             logger.warn(`Stat already active for room ${normalizedCode}, ignoring new submission`);
             return;
         }
         room.activeStat = { socketId: socket.id, cardIndex, stat, value };
         room.challengeResponses.clear();
         logger.info(`Stat quoted by ${socket.id} in room ${normalizedCode}: cardIndex=${cardIndex}, ${stat}=${value}`);
         logger.info(`Room ${normalizedCode} sockets: ${Array.from(room.sockets).join(', ')}`);

         // Emit to active player (confirmation)
         const activeEventData = {
             activePlayer: socket.id,
             stat,
             timeRemaining: 15,
             isConfirmation: true
         };
         io.to(socket.id).emit('challengeInitiated', activeEventData);
         io.to(socket.id).emit('statQuoted', activeEventData);
         logger.info(`Emitted challengeInitiated/statQuoted to active player ${socket.id} in room ${normalizedCode}`);

         // Emit to opponent(s)
         const opponentEventData = {
             activePlayer: socket.id,
             stat,
             timeRemaining: 15,
             isConfirmation: false
         };
         room.sockets.forEach(sid => {
             if (sid !== socket.id && !sid.startsWith('mock_')) {
                 io.to(sid).emit('challengeInitiated', opponentEventData);
                 io.to(sid).emit('statQuoted', opponentEventData);
                 logger.info(`Emitted challengeInitiated/statQuoted to opponent ${sid} in room ${normalizedCode}`);
             }
         });

         startChallengeTimer(normalizedCode);
     });
    socket.on('challenge', ({ roomCode, cardIndex, stat, value }) => {
        const normalizedCode = roomCode.toUpperCase();
        const room = rooms.get(normalizedCode);
        if (!room || !room.activeStat) {
            logger.error(`Challenge failed for socket ${socket.id}: Room ${normalizedCode} not found or no active stat`);
            return socket.emit('error', 'No active stat');
        }
        if (socket.id === room.activeStat.socketId) {
            logger.error(`Challenge failed for socket ${socket.id}: Active player cannot challenge`);
            return socket.emit('error', 'Active player cannot challenge');
        }
        const player = room.gameState.players.find(p => p.socketId === socket.id);
        if (cardIndex < 0 || cardIndex >= player.cards.length) {
            logger.error(`Invalid cardIndex ${cardIndex} for socket ${socket.id}`);
            return socket.emit('error', 'Invalid card index');
        }
        if (stat !== room.activeStat.stat) {
            logger.error(`Invalid stat ${stat} for socket ${socket.id}: Must match ${room.activeStat.stat}`);
            return socket.emit('error', `Must challenge with ${room.activeStat.stat}`);
        }
        room.challengeResponses.set(socket.id, { cardIndex, stat, value, action: 'challenge' });
        logger.info(`Challenge submitted by ${socket.id} in room ${normalizedCode}: cardIndex=${cardIndex}, ${stat}=${value}`);
        checkChallengeResponses(normalizedCode);
    });
    socket.on('gaveUp', ({ roomCode }) => {
        const normalizedCode = roomCode.toUpperCase();
        const room = rooms.get(normalizedCode);
        if (!room || !room.activeStat) {
            logger.error(`GaveUp failed for socket ${socket.id}: Room ${normalizedCode} not found or no active stat`);
            return socket.emit('error', 'No active stat');
        }
        if (socket.id === room.activeStat.socketId) {
            logger.error(`GaveUp failed for socket ${socket.id}: Active player cannot give up`);
            return socket.emit('error', 'Active player cannot give up');
        }
        room.challengeResponses.set(socket.id, { action: 'gaveUp' });
        logger.info(`GaveUp by ${socket.id} in room ${normalizedCode}`);
        checkChallengeResponses(normalizedCode);
    });
});

function startChallengeTimer(roomCode) {
    const room = rooms.get(roomCode);
    let timeRemaining = 15;
    const interval = setInterval(() => {
        timeRemaining--;
        if (timeRemaining >= 0) {
            room.sockets.forEach(sid => {
                if (!sid.startsWith('mock_')) {
                    io.to(sid).emit('statQuoted', {
                        activePlayer: room.activeStat.socketId,
                        stat: room.activeStat.stat,
                        timeRemaining
                    });
                }
            });
            logger.info(`Challenge timer for ${roomCode}: ${timeRemaining}s`);
        }
        if (timeRemaining < 0) {
            clearInterval(interval);
            room.sockets.forEach(sid => {
                if (sid !== room.activeStat.socketId && !room.challengeResponses.has(sid)) {
                    room.challengeResponses.set(sid, { action: 'gaveUp' });
                    logger.info(`Auto-GaveUp for ${sid} in room ${roomCode} due to timeout`);
                }
            });
            processRoundResult(roomCode);
        }
    }, 1000);
}

function checkChallengeResponses(roomCode) {
    const room = rooms.get(roomCode);
    const opponents = room.sockets.filter(sid => sid !== room.activeStat.socketId);
    if (room.challengeResponses.size === opponents.length) {
        processRoundResult(roomCode);
    }
}

function processRoundResult(roomCode) {
    const room = rooms.get(roomCode);
    if (!room || !room.gameState || !room.activeStat) {
        logger.error(`Process round failed: Room ${roomCode} not found or game not started`);
        return;
    }

    const activePlayer = room.gameState.players.find(p => p.socketId === room.activeStat.socketId);
    const activeValue = room.activeStat.value * statWeights[room.activeStat.stat];
    const challengers = Array.from(room.challengeResponses.entries()).filter(([_, res]) => res.action === 'challenge');
    const gaveUps = Array.from(room.challengeResponses.entries()).filter(([_, res]) => res.action === 'gaveUp');

    let winnerSocketId = room.activeStat.socketId;
    let highestValue = activeValue;
    let highestChallenger = null;

    // Compare challengers
    challengers.forEach(([socketId, res]) => {
        const weightedValue = res.value * statWeights[res.stat];
        if (weightedValue > highestValue) {
            highestValue = weightedValue;
            highestChallenger = { socketId, cardIndex: res.cardIndex };
            winnerSocketId = socketId;
        }
    });

    // Update scores and cards
    if (highestChallenger) {
        // Active player loses
        room.scores.set(activePlayer.socketId, (room.scores.get(activePlayer.socketId) || 0) - 4);
        const activeCard = activePlayer.cards.splice(room.activeStat.cardIndex, 1)[0];
        activePlayer.cardCount--;
        const winnerPlayer = room.gameState.players.find(p => p.socketId == highestChallenger.socketId);
        winnerPlayer.cards.push(activeCard);
        winnerPlayer.cardCount++;
        room.scores.set(highestChallenger.socketId, (room.scores.get(highestChallenger.socketId) || 0) + 1);
        // Other challengers
        challengers.forEach(([socketId, res]) => {
            if (socketId !== highestChallenger.socketId) {
                const player = room.gameState.players.find(p => p.socketId === socketId);
                if (res.value * statWeights[res.stat] > activeValue) {
                    room.scores.set(socketId, (room.scores.get(socketId) || 0) + 1);
                } else {
                    const card = player.cards.splice(res.cardIndex, 1)[0];
                    player.cardCount--;
                    activePlayer.cards.push(card);
                    activePlayer.cardCount++;
                }
            }
        });
    } else {
        // Active player wins
        room.scores.set(activePlayer.socketId, (room.scores.get(activePlayer.socketId) || 0) + 1.5);
        challengers.forEach(([socketId, res]) => {
            const player = room.gameState.players.find(p => p.socketId === socketId);
            const card = player.cards.splice(res.cardIndex, 1)[0];
            player.cardCount--;
            activePlayer.cards.push(card);
            activePlayer.cardCount++;
        });
    }

    // Handle GaveUps
    gaveUps.forEach(([socketId, _], index) => {
        const player = room.gameState.players.find(p => p.socketId === socketId);
        room.scores.set(socketId, (room.scores.get(socketId) || 0) - 1);
        if (player.cardCount > 0) {
            const nextPlayerSocketId = room.sockets[(room.sockets.indexOf(socketId) + 1) % room.sockets.length];
            const nextPlayer = room.gameState.players.find(p => p.socketId === nextPlayerSocketId);
            const card = player.cards.splice(0, 1)[0];
            player.cardCount--;
            nextPlayer.cards.push(card);
            nextPlayer.cardCount++;
        }
    });

    // Check for game end
    const activePlayers = room.gameState.players.filter(p => p.cardCount > 0);
    if (activePlayers.length <= 1) {
        const gameWinner = activePlayers.length === 1 ? activePlayers[0].socketId : null;
        room.sockets.forEach(socketId => {
            if (!socketId.startsWith('mock_')) {
                io.to(socketId).emit('gameEnd', {
                    winner: gameWinner,
                    scores: Object.fromEntries(room.scores)
                });
                logger.info(`Game ended in room ${roomCode}, winner: ${gameWinner || 'none'}`);
            }
        });
        rooms.delete(roomCode);
        return;
    }

    // Proceed to next round
    room.gameState.round++;
    room.gameState.currentTurn = highestChallenger ? highestChallenger.socketId : room.sockets[(room.sockets.indexOf(room.gameState.currentTurn) + 1) % room.sockets.length];
    room.activeStat = null;
    room.challengeResponses.clear();

    room.sockets.forEach((socketId, index) => {
        if (!socketId.startsWith('mock_')) {
            const playerCards = room.gameState.players[index].cards;
            io.to(socketId).emit('roundResult', {
                winner: winnerSocketId,
                stat: room.activeStat ? room.activeStat.stat : '',
                submissions: Object.fromEntries(challengers.map(([sid, res]) => [sid, { stat: res.stat, value: res.value }]).concat(gaveUps.map(([sid, _]) => [sid, { stat: 'gaveUp', value: 0 }]))),
                gameState: {
                    players: room.gameState.players.map(p => ({
                        socketId: p.socketId,
                        cardCount: p.cardCount
                    })),
                    currentTurn: room.gameState.currentTurn,
                    round: room.gameState.round,
                    cards: playerCards
                },
                scores: Object.fromEntries(room.scores)
            });
            logger.info(`Sent roundResult to socket ${socketId} in room ${roomCode}`);
        }
    });
}

server.listen(3000, () => {
    logger.info('Server running on port 3000');
});