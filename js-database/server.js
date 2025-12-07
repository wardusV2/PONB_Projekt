import express from "express";
import cors from "cors";
import { watchHistoryDB } from "./db.js";

const app = express();
app.use(cors());
app.use(express.json());

const PORT = 3001;

/**
 * Zwraca historię oglądania dla użytkownika
 */
app.get("/history/:userId", (req, res) => {
  const userId = Number(req.params.userId);

  const data = watchHistoryDB.filter(
    item => item.id_user === userId
  );

  res.json(data);
});

/**
 * Zwraca najczęściej oglądaną kategorię dla usera
 */
app.get("/history/:userId/favorite-category", (req, res) => {
  const userId = Number(req.params.userId);

  const data = watchHistoryDB.filter(
    item => item.id_user === userId
  );

  const counter = {};

  data.forEach(item => {
    counter[item.id_kategori] =
      (counter[item.id_kategori] || 0) + 1;
  });

  let bestCategory = null;
  let max = 0;

  for (const catId in counter) {
    if (counter[catId] > max) {
      bestCategory = catId;
      max = counter[catId];
    }
  }

  res.json({
    userId: userId,
    mostWatchedCategory: Number(bestCategory)
  });
});

// ✅ najczęściej subskrybowana
app.get("/history/:userId/favorite-subscribed-category", (req, res) => {
  const userId = Number(req.params.userId);

  const data = watchHistoryDB.filter(
    item => item.id_user === userId && item.subscribe === true
  );

  const counter = {};
  data.forEach(item => {
    counter[item.id_kategori] =
      (counter[item.id_kategori] || 0) + 1;
  });

  let bestCategory = null;
  let max = 0;

  for (const catId in counter) {
    if (counter[catId] > max) {
      bestCategory = catId;
      max = counter[catId];
    }
  }

  res.json({ userId, mostSubscribedCategory: Number(bestCategory) });
});

// ✅ najczęściej polubiona
app.get("/history/:userId/favorite-liked-category", (req, res) => {
  const userId = Number(req.params.userId);

  const data = watchHistoryDB.filter(
    item => item.id_user === userId && item.like === true
  );

  const counter = {};
  data.forEach(item => {
    counter[item.id_kategori] =
      (counter[item.id_kategori] || 0) + 1;
  });

  let bestCategory = null;
  let max = 0;

  for (const catId in counter) {
    if (counter[catId] > max) {
      bestCategory = catId;
      max = counter[catId];
    }
  }

  res.json({ userId, mostLikedCategory: Number(bestCategory) });
});

// ===== START SERVER =====
app.listen(PORT, () => {
  console.log(`✅ JS DB działa na http://localhost:${PORT}`);
});