var exports = module.exports = {};
var _ = require('underscore');
var fs = require('fs');

function readKids(parents_text) {
  var lines = parents_text.split('\n');
  var data = [];

  for (var i = 0; i < lines.length; i++) {
    var x = lines[i];
    if (x.match(/__END__/)) break;
    x = x.replace(/#.*/, "");
    if (x.match(/\S/)) {
      var pair = x.split(/\t+/);
      pair[0] = pair[0].replace(/^\s+/, "");
      pair[0] = pair[0].replace(/\s+$/, "");
      data.push(pair);
    }
  }

  var kids = {};
  _.each(data, function(edge) {
    var kid = edge[0];
    var parent = edge[1];
    kids[parent] = kids[parent] || [];
    kids[parent].push(kid);
  });

  return kids;
}

function readLog(log_text) {
  var lines = log_text.split('\n');
  var data = [];

  for (var i = 0; i < lines.length; i++) {
    var x = lines[i];
    if (x.match(/__END__/)) break;
    x = x.replace(/#.*/, "");
    if (x.match(/\S/)) {
      data.push(x.split(';'));
    }
  }
  return data;
}

function moveAside(kids, log) {
  _.each(_.keys(log), function(k) {
    if (_.has(kids, k)) {
      log['_' + k] = log[k];
      delete log[k];
      kids[k].unshift('_' + k);
    }
  });
}

function addAmount(log, acct, date, amount) {
  log[acct] = log[acct] || [];
  log[acct].push({date: date, amount: amount});
}

function collateLog(data) {
  var rv = {};
  for (var i = 0; i < data.length; i++) {
    var x = data[i];
    var ymd = x[0].split('-').map(function(x){return parseInt(x)});
    var d = new Date(ymd[0], ymd[1], ymd[2], 0, 0, 0);
    var amount = x[2];
    if (!amount.match(/\./)) {
      amount += ".00";
    }
    amount = parseInt(amount.replace(/[$,.]/g, ""), 10);
    var src = x[3];
    var dst = x[4];
    addAmount(rv, src, d, -amount);
    addAmount(rv, dst, d, amount);
  }
  return rv;
}

function formatMoney(x, html) {
  var neg = false;
  if (x < 0) {
    x = -x;
    neg = true;
  }
  var minus = neg ? "-" : "";
  x = (x/100).toFixed(2);
  x = x.replace(/.(...)+\./, insertCommas);
  var y = minus + '$' + x;
  if (html && neg) {
    y = '<span class="negative">' + y + '</span>'
  }
  return y;
}

function insertCommas(x) {
  return x.replace(/(.)(..)/g, "$1,$2");
}

function spread(kids, data, which) {
  if (_.has(kids, which)) {
    if (_.has(data, which)) return;
    var acc = [];
    _.each(kids[which], function(kid) {
      spread(kids, data, kid);
      acc = acc.concat(data[kid]);
    });
    data[which] = acc;
  }
  else {
    if (!_.has(data, which)) {
      data[which] = [];
    }
  }
}

function printRow(row) {
  var indent = "";

  if (row.depth >= 0) {
    _.times(row.depth, function() {
      indent += " |";
    });

    if (row.hasKids) {
      console.log(formatLine(indent, ""));
    }
    console.log(formatLine(indent + '-- ' + row.which, row.sum));
  }
}

function getRows(kids, data, which, depth, parent) {
  var rv = [];
  var hasKids = _.has(kids, which);
  var indent = "";

  rv.push({which: which, sum: sum(data[which]),
	   depth: depth, parent: parent, hasKids: hasKids});

  if (hasKids) {
    _.each(kids[which], function (k) {
      rv = rv.concat(getRows(kids, data, k, depth + 1, which));
    });
  }

  return rv;
}

function sum(ts) {
  var acc = 0;
  _.each(ts, function(x) {
    acc += x.amount;
  });
  return acc;
}

function formatLine(x, y) {
  var money = y === "" ? "" : spacePadLeft(formatMoney(y), 14);
  return spacePadRight(x, 37) + "|" + money;
}

function spacePadRight(s, n) {
  var spaces = n - s.length;
  for (var i = 0; i < spaces; i++) s += " ";
  return s;
}

function spacePadLeft(s, n) {
  var spaces = n - s.length;
  var t = "";
  for (var i = 0; i < spaces; i++) t += " ";
  return t + s;
}

function fullReport(log, kids) {
  var col = collateLog(log);
  moveAside(kids, col);
  spread(kids, col, 'Top');
  return getRows(kids, col, 'Top', -1);
}

exports.fullReport = fullReport;
exports.readKids = readKids;
exports.readLog = readLog;
