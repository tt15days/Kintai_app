package com.attendance.app.mapper;

import com.attendance.app.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * 監査ログのデータアクセスを担うMapperインターフェースです。
 */
@Mapper
public interface AuditLogMapper {

    /**
     * IDを指定して監査ログを取得します。
     * 
     * @param id 監査ログID
     * @return 監査ログ。存在しない場合はnull
     */
    AuditLog findById(Integer id);

    /**
     * 全ての監査ログを取得します。
     * 
     * @return 監査ログのリスト
     */
    List<AuditLog> findAll();

    /**
     * 新しい監査ログを登録します。
     * 
     * @param entity 登録する監査ログエンティティ
     * @return 登録された件数
     */
    int insert(AuditLog entity);

    /**
     * 監査ログを更新します。
     * 
     * @param entity 更新する監査ログエンティティ
     * @return 更新された件数
     */
    int update(AuditLog entity);

    /**
     * IDを指定して監査ログを削除します。
     * 
     * @param id 削除対象の監査ログID
     * @return 削除された件数
     */
    int delete(Integer id);
}
