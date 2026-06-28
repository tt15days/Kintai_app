package com.attendance.app.config.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalTime;

/**
 * LocalTime TypeHandler - PostgreSQL TIME 型と Java LocalTime の相互変換
 *
 * PostgreSQL の TIME 型と Java の LocalTime を相互変換するハンドラー。
 */
@MappedTypes(LocalTime.class)
public class LocalTimeTypeHandler extends BaseTypeHandler<LocalTime> {

    /**
     * LocalTime を JDBC Time として設定します。
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalTime parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setTime(i, Time.valueOf(parameter));
    }

    /**
     * 列名を指定して Time を LocalTime に変換します。
     */
    @Override
    public LocalTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Time time = rs.getTime(columnName);
        return time != null ? time.toLocalTime() : null;
    }

    /**
     * 列番号を指定して Time を LocalTime に変換します。
     */
    @Override
    public LocalTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Time time = rs.getTime(columnIndex);
        return time != null ? time.toLocalTime() : null;
    }

    /**
     * ストアドプロシージャ結果の Time を LocalTime に変換します。
     */
    @Override
    public LocalTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Time time = cs.getTime(columnIndex);
        return time != null ? time.toLocalTime() : null;
    }
}
