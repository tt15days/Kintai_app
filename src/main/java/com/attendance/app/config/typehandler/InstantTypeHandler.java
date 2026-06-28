package com.attendance.app.config.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Instant TypeHandler - PostgreSQL TIMESTAMPTZ 型と Java Instant の相互変換
 *
 * PostgreSQL の TIMESTAMPTZ 型（タイムゾーン付きタイムスタンプ）と
 * Java の Instant（UTC）を相互変換するハンドラー。
 * 
 * タイムゾーンを考慮せず、UTC タイムスタンプとして処理します。
 */
@MappedTypes(Instant.class)
public class InstantTypeHandler extends BaseTypeHandler<Instant> {

    /**
     * Instant を JDBC Timestamp として設定します。
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Instant parameter, JdbcType jdbcType)
            throws SQLException {
        // Instant を Timestamp に変換（UTC タイムスタンプ）
        ps.setTimestamp(i, Timestamp.from(parameter));
    }

    /**
     * 列名を指定して Timestamp を Instant に変換します。
     */
    @Override
    public Instant getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        if (timestamp != null) {
            return timestamp.toInstant();
        }
        return null;
    }

    /**
     * 列番号を指定して Timestamp を Instant に変換します。
     */
    @Override
    public Instant getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnIndex);
        if (timestamp != null) {
            return timestamp.toInstant();
        }
        return null;
    }

    /**
     * ストアドプロシージャ結果の Timestamp を Instant に変換します。
     */
    @Override
    public Instant getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Timestamp timestamp = cs.getTimestamp(columnIndex);
        if (timestamp != null) {
            return timestamp.toInstant();
        }
        return null;
    }
}
