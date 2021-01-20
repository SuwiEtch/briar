package org.briarproject.bramble.db;

import org.briarproject.bramble.api.db.DbException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.db.JdbcUtils.tryToClose;

class Migration47_48 implements Migration<Connection> {

	private static final Logger LOG = getLogger(Migration47_48.class.getName());

	@Override
	public int getStartVersion() {
		return 47;
	}

	@Override
	public int getEndVersion() {
		return 48;
	}

	@Override
	public void migrate(Connection txn) throws DbException {
		Statement s = null;
		try {
			s = txn.createStatement();
			// Null if message shouldn't be deleted automatically
			s.execute("ALTER TABLE messages"
					+ " ADD COLUMN autoDeleteDuration BIGINT");
			// Null if message shouldn't be deleted automatically or
			// timer hasn't started yet
			s.execute("ALTER TABLE messages"
					+ " ADD COLUMN autoDeleteDeadline BIGINT");
			s.execute("ALTER TABLE messages"
					+ " ADD COLUMN autoDeleteBlocked BOOLEAN DEFAULT FALSE");
			s.execute("CREATE INDEX IF NOT EXISTS messagesByAutoDeleteDeadline"
					+ " ON messages (autoDeleteDeadline)");
		} catch (SQLException e) {
			tryToClose(s, LOG, WARNING);
			throw new DbException(e);
		}
	}
}
