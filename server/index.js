require('dotenv').config();
     const express = require('express');
     const http = require('http');
     const { Server } = require('socket.io');
     const winston = require('winston');
     const mongoose = require('mongoose');
     const cors = require('cors');

     const app = express();
     const server = http.createServer(app);
     const io = new Server(server, {
         cors: {
             origin: '*', // Allow all origins for testing
             methods: ['GET', 'POST']
         }
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
     app.use(cors()); // Enable CORS for HTTP requests
     app.use(express.json());

     // Room management
     const rooms = new Map(); // { roomCode: { maxPlayers, playerCount, sockets } }

     function generateRoomCode() {
         return Math.random().toString(36).substring(2, 8).toUpperCase();
     }

     // HTTP endpoints
     app.get('/health', (req, res) => {
         res.status(200).json({ status: 'Server is running' });
     });

     app.post('/create-room', (req, res) => {
         const { maxPlayers } = req.body;
         if (!maxPlayers || maxPlayers < 2 || maxPlayers > 6) {
             return res.status(400).json({ error: 'maxPlayers must be 2-6' });
         }
         const roomCode = generateRoomCode();
         rooms.set(roomCode, { maxPlayers, playerCount: 1, sockets: [] });
         logger.info(`Room created: ${roomCode}, maxPlayers: ${maxPlayers}`);
         res.status(200).json({ roomCode, playerCount: 1, maxPlayers });
     });
     app.get('/cards', async (req, res) => {
        const Card = require('./models/Card');
        try {
            const cards = await Card.find().limit(5);
            res.json(cards);
        } catch (err) {
            logger.error('Error fetching cards:', err.message);
            res.status(500).json({ error: 'Server error' });
        }
    });

     app.post('/join-room', (req, res) => {
         const { roomCode } = req.body;
         const room = rooms.get(roomCode);
         if (!room) {
             return res.status(404).json({ error: 'Room not found' });
         }
         if (room.playerCount >= room.maxPlayers) {
             return res.status(400).json({ error: 'Room is full' });
         }
         room.playerCount += 1;
         logger.info(`Player joined room: ${roomCode}, playerCount: ${room.playerCount}`);
         res.status(200).json({ roomCode, playerCount: room.playerCount, maxPlayers: room.maxPlayers });

         if (room.playerCount === room.maxPlayers) {
             startCountdown(roomCode);
         }
     });

     // WebSocket connection
     io.on('connection', (socket) => {
         logger.info('Client connected:', socket.id);

         socket.on('joinRoom', (roomCode) => {
             const room = rooms.get(roomCode);
             if (room) {
                 socket.join(roomCode);
                 room.sockets.push(socket.id);
                 logger.info(`Socket ${socket.id} joined room ${roomCode}`);
             }
         });

         socket.on('disconnect', () => {
             logger.info('Client disconnected:', socket.id);
             for (const [roomCode, room] of rooms) {
                 if (room.sockets.includes(socket.id)) {
                     room.sockets = room.sockets.filter(id => id !== socket.id);
                     room.playerCount = Math.max(0, room.playerCount - 1);
                     logger.info(`Player left room: ${roomCode}, playerCount: ${room.playerCount}`);
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

     // Start server
     const PORT = 3000;
     server.listen(PORT, () => {
         logger.info(`Server running on port ${PORT}`);
     });