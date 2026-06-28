package com.attendance.app.config.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import com.attendance.app.entity.UserRole;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * UserRole を DB の文字列値と相互変換する TypeHandler です。
 */
public class UserRoleTypeHandler extends BaseTypeHandler<UserRole> {

    /**
     * UserRole を文字列として JDBC パラメータへ設定します。
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UserRole parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.name());
    }

    /**
     * 列名を指定して文字列値を UserRole に変換します。
     */
    @Override
    public UserRole getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value == null ? null : UserRole.valueOf(value);
    }

    /**
     * 列番号を指定して文字列値を UserRole に変換します。
     */
    @Override
    public UserRole getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null ? null : UserRole.valueOf(value);
    }

    /**
     * ストアドプロシージャ結果の文字列値を UserRole に変換します。
     */
    @Override
    public UserRole getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value == null ? null : UserRole.valueOf(value);
    }
}
