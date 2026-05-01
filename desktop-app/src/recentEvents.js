const { RECENT_EVENT_LIMIT, RECENT_EVENT_TTL_MS } = require('./constants');

class RecentEvents {
  constructor() {
    this.events = new Map();
  }

  has(eventId) {
    this.prune();
    return this.events.has(eventId);
  }

  add(eventId) {
    if (!eventId) {
      return;
    }
    this.events.set(eventId, Date.now());
    this.prune();
  }

  prune() {
    const now = Date.now();
    for (const [eventId, timestamp] of this.events.entries()) {
      if (now - timestamp > RECENT_EVENT_TTL_MS || this.events.size > RECENT_EVENT_LIMIT) {
        this.events.delete(eventId);
      }
    }
  }
}

module.exports = RecentEvents;
