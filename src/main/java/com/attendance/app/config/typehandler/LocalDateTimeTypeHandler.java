package com.attendance.app.config.typehandler;

/**
 * {@link OffsetDateTimeTypeHandler} に置き換えられたレガシークラスです。
 *
 * <p>このクラスは後方互換のために残されており、新規コードでは
 * {@link OffsetDateTimeTypeHandler} を使用してください。
 * MyBatis の型マッピング登録（{@code @MappedTypes}）は削除済みのため、
 * このハンドラは自動登録されません。
 *
 * @deprecated {@link OffsetDateTimeTypeHandler} を使用してください。
 */
@Deprecated
public class LocalDateTimeTypeHandler extends OffsetDateTimeTypeHandler {
}
