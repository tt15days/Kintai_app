package com.attendance.app.config.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

/**
 * JDBC TIMESTAMPTZ と OffsetDateTime を相互変換する TypeHandler です。
 *
 * PostgreSQL JDBC 42.x 以降のネイティブ OffsetDateTime サポートを利用し、
 * タイムゾーン情報を保持したまま DB との値の読み書きを行います。
 */
@MappedTypes(OffsetDateTime.class)
public class OffsetDateTimeTypeHandler extends BaseTypeHandler<OffsetDateTime> {

    /**
     * OffsetDateTime を JDBC オブジェクトとして設定します。
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, OffsetDateTime parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter);
    }

    /**
     * 列名を指定して TIMESTAMPTZ を OffsetDateTime に変換します。
     */
    @Override
    public OffsetDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getObject(columnName, OffsetDateTime.class);
    }

    /**
     * 列番号を指定して TIMESTAMPTZ を OffsetDateTime に変換します。
     */
    @Override
    public OffsetDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getObject(columnIndex, OffsetDateTime.class);
    }

    /**
     * ストアドプロシージャ結果の TIMESTAMPTZ を OffsetDateTime に変換します。
     */
    @Override
    public OffsetDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getObject(columnIndex, OffsetDateTime.class);
    }
}
