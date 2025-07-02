const mongoose = require('mongoose');

const cardSchema = new mongoose.Schema({
    playerName: String,
    runs: Number,
    wickets: Number,
    battingAverage: Number,
    strikeRate: Number,
    matchesPlayed: Number,
    centuries: Number,
    fiveWicketHauls: Number,
    Economy: Number, // Bowling economy rate
    format: String   // e.g., 'ODI', 'T20', etc.
});

module.exports = mongoose.model('Card', cardSchema,'players'); // 'players' is the collection name in MongoDB