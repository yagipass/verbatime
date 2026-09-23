package io.github.yagipass.verbatime.examples.mybatis;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderMapper {

    @Insert("insert into orders (sku, qty, cents, tx_id) values (#{sku}, #{qty}, #{cents}, #{txId})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(OrderRow row);

    @Select("select id, sku, qty, cents, tx_id, created_at from orders where id = #{id}")
    Optional<OrderRow> findById(long id);

    @Select("select id, sku, qty, cents, tx_id, created_at from orders order by id desc limit 10")
    List<OrderRow> findRecent();
}
