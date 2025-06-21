const mongoose = require('mongoose');

const cardSchema = new mongoose.Schema({
    playerName: String,
    runs: Number,
    wickets: Number,
    battingAverage: Number,
    strikeRate: Number,
    matchesPlayed: Number,
    centuries: Number,
    fiveWicketHauls: Number
});

module.exports = mongoose.model('Card', cardSchema);