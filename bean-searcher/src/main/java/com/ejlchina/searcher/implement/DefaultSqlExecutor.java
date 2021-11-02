package com.ejlchina.searcher.implement;

import com.ejlchina.searcher.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;

/**
 * JDBC Search Sql 执行器
 * 
 * @author Troy.Zhou
 * @since 1.1.1
 * 
 */
public class DefaultSqlExecutor implements SqlExecutor {


	protected Logger log = LoggerFactory.getLogger(DefaultSqlExecutor.class);
	
	
	private DataSource dataSource;

	/**
	 * 是否使用只读事务
	 */
	private boolean transactional = false;
	
	public DefaultSqlExecutor() {
	}
	
	public DefaultSqlExecutor(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	
	@Override
	public <T> SqlResult<T> execute(SearchSql<T> searchSql) {
		if (!searchSql.isShouldQueryList() && !searchSql.isShouldQueryCluster()) {
			return new SqlResult<>(searchSql);
		}
		if (dataSource == null) {
			throw new SearchException("You must config a dataSource for MainSearchSqlExecutor!");
		}
		Connection connection;
		try {
			connection = getConnection(searchSql.getBeanMeta());
		} catch (SQLException e) {
			throw new SearchException("Can not get connection from dataSource!", e);
		}
		try {
			return doExecute(searchSql, connection);
		} catch (SQLException e) {
			// 如果有异常，则立马关闭，否则与 SqlResult 一起关闭
			closeConnection(connection);
			throw new SearchException("A exception occurred when query!", e);
		}
	}

	protected Connection getConnection(BeanMeta<?> beanMeta) throws SQLException {
		return dataSource.getConnection();
	}

	protected <T> SqlResult<T> doExecute(SearchSql<T> searchSql, Connection connection) throws SQLException {
		if (transactional) {
			connection.setAutoCommit(false);
			connection.setReadOnly(true);
		}
		SqlResult<T> result = new SqlResult<T>(searchSql) {
			@Override
			public void close() {
				try {
					super.close();
				} finally {
					closeConnection(connection);
				}
			}
		};
		if (searchSql.isShouldQueryList()) {
			String sql = searchSql.getListSqlString();
			List<Object> params = searchSql.getListSqlParams();
			writeLog(sql, params);
			executeListSqlAndCollectResult(connection, sql, params, result);
		}
		if (searchSql.isShouldQueryCluster()) {
			String sql = searchSql.getClusterSqlString();
			List<Object> params = searchSql.getClusterSqlParams();
			writeLog(sql, params);
			executeClusterSqlAndCollectResult(connection, sql, params, result);
		}
		if (transactional) {
			connection.commit();
			connection.setReadOnly(false);
		}
		return result;
	}

	protected void writeLog(String sql, List<Object> params) {
		log.debug("bean-searcher - sql ---- {}", sql);
		log.debug("bean-searcher - params - {}", params);
	}

	protected void executeListSqlAndCollectResult(Connection connection, String sql, List<Object> params,
				SqlResult<?> sqlResult) throws SQLException {
		PreparedStatement statement = connection.prepareStatement(sql);
		setStatementParams(statement, params);
		ResultSet resultSet = statement.executeQuery();
		sqlResult.setListResult(resultSet, statement);
	}

	protected void executeClusterSqlAndCollectResult(Connection connection, String sqlString, List<Object> sqlParams,
				SqlResult<?> sqlResult) throws SQLException {
		PreparedStatement statement = connection.prepareStatement(sqlString);
		setStatementParams(statement, sqlParams);
		ResultSet resultSet = statement.executeQuery();
		sqlResult.setClusterResult(resultSet, statement);
	}

	protected void setStatementParams(PreparedStatement statement, List<Object> params) throws SQLException {
		for (int i = 0; i < params.size(); i++) {
			statement.setObject(i + 1, params.get(i));
		}
	}

	protected void closeConnection(Connection connection) {
		try {
			if (connection != null) {
				connection.close();
			}
		} catch (SQLException e) {
			throw new SearchException("Can not close connection!", e);
		}
	}

	/**
	 * 设置数据源
	 *
	 * @param dataSource
	 *            数据源
	 */
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public DataSource getDataSource() {
		return dataSource;
	}

	public boolean isTransactional() {
		return transactional;
	}

	/**
	 * 设置是否使用只读事务
	 * @param transactional 是否使用事务
	 */
	public void setTransactional(boolean transactional) {
		this.transactional = transactional;
	}

}
