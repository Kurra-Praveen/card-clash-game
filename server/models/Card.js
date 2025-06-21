const mongoose = require('mongoose');

const cardSchema = new mongoose.Schema({
    playerName: { type: String, required: true },
    runs: { type: Number, required: true },
    wickets: { type: Number, required: true },
    battingAverage: { type: Number, required: true },
    strikeRate: { type: Number, required: true },
    matchesPlayed: { type: Number, required: true },
    centuries: { type: Number, required: true },
    fiveWicketHauls: { type: Number, required: true }
});

module.exports = mongoose.model('Card', cardSchema);