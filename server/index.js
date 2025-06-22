require('dotenv').config();
     const express = require('express');
     const http = require('http');
     const { Server } = require('socket.io');
     const winston = require('winston');
     const mongoose = require('mongoose');
     const cors = require('cors');
     const Card = require('./models/Card');

     const app = express();
     const server = http.createServer(app);
     const io = new Server(server, {
         cors: { origin: '*', methods: ['GET', 'POST'] }
     });

     // Logger setup
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

     // MongoDB connection
     mongoose.connect(process.env.MONGODB_URI, {
         useNewUrlParser: true,
         useUnifiedTopology: true
     }).then(() => {
         logger.info('Connected to MongoDB Atlas');
     }).catch(err => {
         logger.error('MongoDB Atlas connection failed:', err.message);
     });

     // Middleware
     app.use(cors());
     app.use(express.json());

     // Room management
     const rooms = new Map(); // { roomCode: { maxPlayers, playerCount, sockets, gameState } }

     function generateRoomCode() {
         return Math.random().toString(36).substring(2, 8).toUpperCase();
     }

     // Shuffle array
     function shuffle(array) {
         for (let i = array.length - 1; i > 0; i--) {
             const j = Math.floor(Math.random() * (i + 1));
             [array[i], array[j]] = [array[j], array[i]];
         }
         return array;
     }

     // HTTP endpoints
     app.get('/health', (req, res) => {
         res.status(200).json({ status: 'Server is running' });
     });

     app.get('/cards', async (req, res) => {
         try {
             const cards = await Card.find().limit(5);
             res.json(cards);
         } catch (err) {
             logger.error('Error fetching cards:', err.message);
             res.status(500).json({ error: 'Server error' });
         }
     });

     app.post('/create-room', (req, res) => {
         const { maxPlayers } = req.body;
         if (!maxPlayers || maxPlayers < 2 || maxPlayers > 6) {
             return res.status(400).json({ error: 'maxPlayers must be 2-6' });
         }
         const roomCode = generateRoomCode();
         rooms.set(roomCode, { maxPlayers, playerCount: 1, sockets: [], gameState: null });
         logger.info(`Room created: ${roomCode}, maxPlayers: ${maxPlayers}`);
         res.status(200).json({ roomCode, playerCount: 1, maxPlayers });
     });

     app.post('/join-room', (req, res) => {
        const { roomCode } = req.body;
        const room = rooms.get(roomCode);
        if (!room) {
            logger.error(`Join room failed: Room ${roomCode} not found`);
            return res.status(404).json({ error: 'Room not found' });
        }
        if (room.playerCount >= room.maxPlayers) {
            logger.error(`Join room failed: Room ${roomCode} is full`);
            return res.status(400).json({ error: 'Room is full' });
        }
        room.playerCount += 1;
        // Mock socket ID for Postman-joined players
        if (!room.mockSockets) room.mockSockets = [];
        const mockSocketId = `mock_${Math.random().toString(36).substring(2, 10)}`;
        room.mockSockets.push(mockSocketId);
        room.sockets.push(mockSocketId); // Add mock socket to sockets for testing
        logger.info(`Player joined room: ${roomCode}, playerCount: ${room.playerCount}, sockets: ${room.sockets.length}`);
        res.status(200).json({ roomCode, playerCount: room.playerCount, maxPlayers: room.maxPlayers });
    
        if (room.playerCount === room.maxPlayers) {
            startCountdown(roomCode);
        }
    }); 

    app.post('/start-game', async (req, res) => {
        const { roomCode } = req.body;
        const room = rooms.get(roomCode);
        if (!room) {
            logger.error(`Start game failed: Room ${roomCode} not found`);
            return res.status(404).json({ error: 'Room not found' });
        }
        logger.info(`Starting game for room ${roomCode}, sockets: ${room.sockets.length}, playerCount: ${room.playerCount}`);
        try {
            // Fetch 20 cards (5 per player for maxPlayers)
            const cardsNeeded = room.maxPlayers * 5;
            const cards = await Card.find().limit(cardsNeeded);
            if (cards.length < cardsNeeded) {
                logger.error(`Start game failed: Only ${cards.length} cards available, need ${cardsNeeded}`);
                return res.status(400).json({ error: `Insufficient cards in database, need ${cardsNeeded}` });
            }
            const shuffledCards = shuffle([...cards]);
            const players = room.sockets.map((socketId, index) => ({
                socketId,
                cards: shuffledCards.slice(index * 5, (index + 1) * 5),
                score: 0
            }));
            room.gameState = {
                players,
                currentTurn: 0,
                round: 1,
                maxRounds: 10,
                selectedStats: []
            };
            // Emit gameData only to real sockets (exclude mocks)
            const realSockets = room.sockets.filter(id => !id.startsWith('mock_'));
            realSockets.forEach(socketId => {
                io.to(socketId).emit('gameData', {
                    players: players.map(p => ({ socketId: p.socketId, cardCount: p.cards.length })),
                    currentTurn: players[0].socketId,
                    round: 1,
                    cards: players.find(p => p.socketId === socketId)?.cards || []
                });
            });
            emitGameData(roomCode, room);
            logger.info(`Game initialized for room: ${roomCode}, players: ${room.sockets.length}`);
            res.status(200).json({ message: 'Game started' });
        } catch (err) {
            logger.error(`Error starting game for room ${roomCode}: ${err.message}`, err);
            res.status(500).json({ error: 'Server error' });
        }
    });

     // WebSocket connection
     io.on('connection', (socket) => {
         logger.info('Client connected:', socket.id);

         socket.on('joinRoom', (roomCode) => {
            const room = rooms.get(roomCode);
            if (room) {
                socket.join(roomCode);
                // Replace a mock socket if available
                if (room.mockSockets && room.mockSockets.length > 0) {
                    const mockSocketId = room.mockSockets.shift();
                    room.sockets = room.sockets.filter(id => id !== mockSocketId);
                }
                if (!room.sockets.includes(socket.id)) {
                    room.sockets.push(socket.id);
                }
                logger.info(`Socket ${socket.id} joined room ${roomCode}, sockets: ${room.sockets.length}`);
            } else {
                logger.error(`Socket ${socket.id} failed to join room ${roomCode}: Room not found`);
            }
        });

         socket.on('submitStat', ({ roomCode, stat, value, challenge }) => {
             const room = rooms.get(roomCode);
             if (!room || !room.gameState) return;
             const playerIndex = room.gameState.players.findIndex(p => p.socketId === socket.id);
             if (playerIndex !== room.gameState.currentTurn) return;

             // Add stat selection (and challenge/quote if present)
             room.gameState.selectedStats.push({ socketId: socket.id, stat, value, challenge });
             if (room.gameState.selectedStats.length === room.playerCount) {
                 // --- Advanced Challenge/Quote Logic (FRD extension) ---
                 // Example: If challenge is present, resolve challenge (bluff, stat reveal, etc.)
                 // For now, we proceed as normal, but you can expand this block for advanced rules.

                 // Determine winner (highest stat value)
                 const winner = room.gameState.selectedStats.reduce((prev, curr) =>
                     curr.value > prev.value ? curr : prev
                 );
                 const winnerIndex = room.gameState.players.findIndex(p => p.socketId === winner.socketId);
                 room.gameState.players[winnerIndex].score += 1;

                 // Remove played cards and collect them for redistribution
                 const playedCards = [];
                 room.gameState.players.forEach(p => {
                     if (p.cards.length > 0) playedCards.push(p.cards.shift());
                 });
                 // Winner gets all played cards (if any)
                 playedCards.forEach(card => {
                     if (card) room.gameState.players[winnerIndex].cards.push(card);
                 });

                 // Eliminate players with no cards left
                 room.gameState.players = room.gameState.players.filter(p => p.cards.length > 0);
                 room.playerCount = room.gameState.players.length;
                 // If currentTurn player eliminated, reset to 0
                 if (room.gameState.currentTurn >= room.playerCount) {
                     room.gameState.currentTurn = 0;
                 }

                 // Prepare scores map
                 const scores = {};
                 room.gameState.players.forEach(p => { scores[p.socketId] = p.score; });
                 // Check game end
                 if (room.playerCount <= 1 || room.gameState.round >= room.gameState.maxRounds) {
                     const gameWinner = room.gameState.players.reduce((prev, curr) =>
                         curr.score > prev.score ? curr : prev
                     );
                     io.to(roomCode).emit('gameEnd', {
                         winner: gameWinner ? gameWinner.socketId : null,
                         scores
                     });
                     rooms.delete(roomCode);
                     logger.info(`Game ended for room: ${roomCode}`);
                 } else {
                     room.gameState.round += 1;
                     room.gameState.currentTurn = (room.gameState.currentTurn + 1) % room.playerCount;
                     const submissions = buildSubmissions(room.gameState.selectedStats);
                     room.gameState.selectedStats = [];
                     io.to(roomCode).emit('roundResult', {
                         winner: winner.socketId,
                         stat,
                         submissions,
                         scores,
                         gameState: {
                             players: room.gameState.players.map(p => ({ socketId: p.socketId, cardCount: p.cards.length })),
                             currentTurn: room.gameState.players[room.gameState.currentTurn].socketId,
                             round: room.gameState.round,
                             cards: [], // Cards are sent individually in gameData
                             scores
                         }
                     });
                     emitGameData(roomCode, room);
                 }
                 logger.info(`Round ${room.gameState.round} completed for room: ${roomCode}`);
             }
         });

         // Handle challenge from opponent
         socket.on('challenge', ({ roomCode, cardIndex, stat, value }) => {
            const room = rooms.get(roomCode);
            if (!room || !room.gameState) return;
            room.gameState.selectedStats.push({ socketId: socket.id, stat, value, challenge: true });
            if (room.gameState.selectedStats.length === room.playerCount) {
                const winner = room.gameState.selectedStats.reduce((prev, curr) =>
                    curr.value > prev.value ? curr : prev
                );
                const winnerIndex = room.gameState.players.findIndex(p => p.socketId === winner.socketId);
                room.gameState.players[winnerIndex].score += 1;
                const playedCards = [];
                room.gameState.players.forEach(p => {
                    if (p.cards.length > 0) playedCards.push(p.cards.shift());
                });
                playedCards.forEach(card => {
                    if (card) room.gameState.players[winnerIndex].cards.push(card);
                });
                room.gameState.players = room.gameState.players.filter(p => p.cards.length > 0);
                room.playerCount = room.gameState.players.length;
                if (room.gameState.currentTurn >= room.playerCount) {
                    room.gameState.currentTurn = 0;
                }
                const scores = {};
                room.gameState.players.forEach(p => { scores[p.socketId] = p.score; });
                if (room.playerCount <= 1 || room.gameState.round >= room.gameState.maxRounds) {
                    const gameWinner = room.gameState.players.reduce((prev, curr) =>
                        curr.score > prev.score ? curr : prev
                    );
                    io.to(roomCode).emit('gameEnd', {
                        winner: gameWinner ? gameWinner.socketId : null,
                        scores
                    });
                    rooms.delete(roomCode);
                    logger.info(`Game ended for room: ${roomCode}`);
                } else {
                    room.gameState.round += 1;
                    room.gameState.currentTurn = (room.gameState.currentTurn + 1) % room.playerCount;
                    const submissions = buildSubmissions(room.gameState.selectedStats);
                    room.gameState.selectedStats = [];
                    io.to(roomCode).emit('roundResult', {
                        winner: winner.socketId,
                        stat,
                        submissions,
                        scores,
                        gameState: {
                            players: room.gameState.players.map(p => ({ socketId: p.socketId, cardCount: p.cards.length })),
                            currentTurn: room.gameState.players[room.gameState.currentTurn].socketId,
                            round: room.gameState.round,
                            cards: [],
                            scores
                        }
                    });
                    emitGameData(roomCode, room);
                }
                logger.info(`Round ${room.gameState.round} completed for room: ${roomCode}`);
            }
        });

        // Handle gave up (player forfeits the round)
        socket.on('gaveUp', ({ roomCode }) => {
            const room = rooms.get(roomCode);
            if (!room || !room.gameState) return;
            const player = room.gameState.players.find(p => p.socketId === socket.id);
            if (player) player.cards = [];
            room.gameState.players = room.gameState.players.filter(p => p.cards.length > 0);
            room.playerCount = room.gameState.players.length;
            if (room.gameState.currentTurn >= room.playerCount) {
                room.gameState.currentTurn = 0;
            }
            const scores = {};
            room.gameState.players.forEach(p => { scores[p.socketId] = p.score; });
            if (room.playerCount <= 1 || room.gameState.round >= room.gameState.maxRounds) {
                const gameWinner = room.gameState.players.reduce((prev, curr) =>
                    curr.score > prev.score ? curr : prev
                );
                io.to(roomCode).emit('gameEnd', {
                    winner: gameWinner ? gameWinner.socketId : null,
                    scores
                });
                rooms.delete(roomCode);
                logger.info(`Game ended for room: ${roomCode}`);
            } else {
                room.gameState.round += 1;
                room.gameState.currentTurn = (room.gameState.currentTurn + 1) % room.playerCount;
                room.gameState.selectedStats = [];
                io.to(roomCode).emit('roundResult', {
                    winner: null,
                    stat: null,
                    submissions: {},
                    scores,
                    gameState: {
                        players: room.gameState.players.map(p => ({ socketId: p.socketId, cardCount: p.cards.length })),
                        currentTurn: room.gameState.players[room.gameState.currentTurn].socketId,
                        round: room.gameState.round,
                        cards: [],
                        scores
                    }
                });
                emitGameData(roomCode, room);
            }
            logger.info(`Player ${socket.id} gave up in room: ${roomCode}`);
        });

         socket.on('disconnect', () => {
            logger.info('Client disconnected:', socket.id);
            for (const [roomCode, room] of rooms) {
                if (room.sockets.includes(socket.id)) {
                    room.sockets = room.sockets.filter(id => id !== socket.id);
                    room.playerCount = Math.max(0, room.playerCount - 1);
                    logger.info(`Player left room: ${roomCode}, playerCount: ${room.playerCount}, sockets: ${room.sockets.length}`);
                    if (room.gameState && room.playerCount <= 1) {
                        io.to(roomCode).emit('gameEnd', { message: 'Game ended due to player disconnect' });
                        rooms.delete(roomCode);
                        logger.info(`Room ${roomCode} deleted due to insufficient players`);
                    }
                    break;
                }
            }
        });
     });

     function startCountdown(roomCode) {
         let countdown = 3;
         const interval = setInterval(() => {
             io.to(roomCode).emit('countdown', countdown);
             logger.info(`Countdown for ${roomCode}: ${countdown}`);
             countdown -= 1;
             if (countdown < 1) {
                 clearInterval(interval);
                 io.to(roomCode).emit('gameStart');
                 logger.info(`Game started for room: ${roomCode}`);
             }
         }, 1000);
     }

     function emitGameData(roomCode, room) {
        room.gameState.players.forEach(player => {
            io.to(player.socketId).emit('gameData', {
                players: room.gameState.players.map(p => ({ socketId: p.socketId, cardCount: p.cards.length })),
                currentTurn: room.gameState.players[room.gameState.currentTurn].socketId,
                round: room.gameState.round,
                cards: player.cards,
                scores: Object.fromEntries(room.gameState.players.map(p => [p.socketId, p.score]))
            });
        });
    }

     // Start server
     const PORT = 3000;
     server.listen(PORT, () => {
         logger.info(`Server running on port ${PORT}`);
     });