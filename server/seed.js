require('dotenv').config();
  const mongoose = require('mongoose');
  const Card = require('./models/Card');
  const winston = require('winston');

  const logger = winston.createLogger({
      level: 'info',
      format: winston.format.combine(
          winston.format.timestamp(),
          winston.format.json()
      ),
      transports: [new winston.transports.Console()]
  });

  mongoose.connect(process.env.MONGODB_URI, {
      useNewUrlParser: true,
      useUnifiedTopology: true
  }).then(async () => {
      logger.info('Connected to MongoDB Atlas for seeding');

      // Realistic cricket player names
      const playerNames = [
          "Virat Kohli", "Sachin Tendulkar", "Rohit Sharma", "MS Dhoni", "Jasprit Bumrah",
          "Rahul Dravid", "Virender Sehwag", "Yuvraj Singh", "Ravindra Jadeja", "Shikhar Dhawan",
          "Kapil Dev", "Sunil Gavaskar", "Anil Kumble", "Harbhajan Singh", "Rishabh Pant",
          "KL Rahul", "Cheteshwar Pujara", "Ajinkya Rahane", "Ishant Sharma", "Mohammed Shami",
          // Add more for 50 total
          "Sourav Ganguly", "VVS Laxman", "Zaheer Khan", "Gautam Gambhir", "Suresh Raina",
          "Hardik Pandya", "Dinesh Karthik", "Ravichandran Ashwin", "Umesh Yadav", "Bhuvneshwar Kumar",
          "Prithvi Shaw", "Shreyas Iyer", "Sanju Samson", "Wriddhiman Saha", "Kuldeep Yadav",
          "Yuzvendra Chahal", "Mohammed Siraj", "Shardul Thakur", "Axar Patel", "Kedar Jadhav",
          "Navdeep Saini", "Deepak Chahar", "Mayank Agarwal", "Hanuma Vihari", "Ravi Shastri",
          "Irfan Pathan", "Munaf Patel", "Parthiv Patel", "Ambati Rayudu", "Murali Vijay"
      ];

      // Sample data (50 cards)
      const sampleCards = playerNames.map((name) => ({
          playerName: name,
          runs: Math.floor(Math.random() * 10000),
          wickets: Math.floor(Math.random() * 500),
          battingAverage: Math.random() * 100,
          strikeRate: Math.random() * 200,
          matchesPlayed: Math.floor(Math.random() * 300),
          centuries: Math.floor(Math.random() * 50),
          fiveWicketHauls: Math.floor(Math.random() * 10)
      }));

      await Card.deleteMany({}); // Clear existing data
      await Card.insertMany(sampleCards);
      logger.info('Inserted 50 sample cards');
      mongoose.connection.close();
  }).catch(err => {
      logger.error('Seeding failed:', err.message);
  });