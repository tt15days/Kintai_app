package com.attendance.app.config.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * LocalDate TypeHandler - PostgreSQL DATE 型と Java LocalDate の相互変換
 *
 * PostgreSQL の DATE 型と Java の LocalDate を相互変換するハンドラー。
 * タイムゾーン不要な日付のみを扱う場合に使用。
 */
@MappedTypes(LocalDate.class)
public class LocalDateTypeHandler extends BaseTypeHandler<LocalDate> {

    /**
     * LocalDate を JDBC Date として設定します。
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalDate parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setDate(i, Date.valueOf(parameter));
    }

    /**
     * 列名を指定して Date を LocalDate に変換します。
     */
    @Override
    public LocalDate getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Date date = rs.getDate(columnName);
        if (date != null) {
            return date.toLocalDate();
        }
        return null;
    }

    /**
     * 列番号を指定して Date を LocalDate に変換します。
     */
    @Override
    public LocalDate getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Date date = rs.getDate(columnIndex);
        if (date != null) {
            return date.toLocalDate();
        }
        return null;
    }

    /**
     * ストアドプロシージャ結果の Date を LocalDate に変換します。
     */
    @Override
    public LocalDate getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Date date = cs.getDate(columnIndex);
        if (date != null) {
            return date.toLocalDate();
        }
        return null;
    }
}
