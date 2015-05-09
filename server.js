var express = require('express');
var journal = require('./journal');
var fs = require('fs');


express()
  .get('/parents', function(req, res) {
    res.json(journal.readKids(fs.readFileSync('../parents.txt', 'utf8')));
  })
  .get('/journal', function(req, res) {
    res.json(journal.readLog(fs.readFileSync('../journal.txt', 'utf8')));
  })
  .get('/report', function(req, res) {
    var log = journal.readLog(fs.readFileSync('../journal.txt', 'utf8'));
    var parents = journal.readKids(fs.readFileSync('../parents.txt', 'utf8'));
    var report = journal.fullReport(log, parents);
    res.json([parents, report]);
  })

  .use(express.static("resources/public"))
  .listen(8001);

console.log('Listening on port 8001.');
