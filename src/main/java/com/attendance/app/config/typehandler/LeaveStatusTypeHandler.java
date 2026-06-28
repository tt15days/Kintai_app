package com.attendance.app.config.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import com.attendance.app.entity.LeaveStatus;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * LeaveStatus を DB の文字列値と相互変換する TypeHandler です。
 */
public class LeaveStatusTypeHandler extends BaseTypeHandler<LeaveStatus> {

    /**
     * LeaveStatus を文字列として JDBC パラメータへ設定します。
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LeaveStatus parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.name());
    }

    /**
     * 列名を指定して文字列値を LeaveStatus に変換します。
     */
    @Override
    public LeaveStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value == null ? null : LeaveStatus.valueOf(value);
    }

    /**
     * 列番号を指定して文字列値を LeaveStatus に変換します。
     */
    @Override
    public LeaveStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null ? null : LeaveStatus.valueOf(value);
    }

    /**
     * ストアドプロシージャ結果の文字列値を LeaveStatus に変換します。
     */
    @Override
    public LeaveStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value == null ? null : LeaveStatus.valueOf(value);
    }
}
