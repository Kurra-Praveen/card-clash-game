require('dotenv').config();
const mongoose = require('mongoose');
const Card = require('../models/Card');
const winston = require('winston');
const fs = require('fs').promises; // For reading JSON file

const logger = winston.createLogger({
    level: 'info',
    format: winston.format.combine(
        winston.format.timestamp(),
        winston.format.json()
    ),
    transports: [new winston.transports.Console()]
});

async function seedDatabase() {
    try {
        // Connect to MongoDB Atlas
        await mongoose.connect(process.env.MONGODB_URI, {
            useNewUrlParser: true,
            useUnifiedTopology: true
        });
        logger.info('Connected to MongoDB Atlas for seeding');

        // Read JSON file
        const jsonData = await fs.readFile('player_stats.json', 'utf8');
        const sampleCards = JSON.parse(jsonData);

        // Validate data against schema (optional but recommended)
        for (const card of sampleCards) {
            const validation = new Card(card).validateSync();
            if (validation) {
                logger.error(`Validation error for card ${card.playerName}:`, validation.errors);
                throw new Error(`Validation failed for ${card.playerName}`);
            }
        }

        // Clear existing data
        await Card.deleteMany({});
        logger.info('Cleared existing data');

        // Insert new data
        await Card.insertMany(sampleCards);
        logger.info(`Inserted ${sampleCards.length} cards from JSON file`);

        // Close connection
        mongoose.connection.close();
        logger.info('Database connection closed');
    } catch (err) {
        console.error('Seeding failed:', err);
        logger.error('Seeding failed:', err.message);
        mongoose.connection.close();
    }
}

seedDatabase();