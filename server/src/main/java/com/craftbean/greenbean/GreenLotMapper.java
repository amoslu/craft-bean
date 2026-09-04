package com.craftbean.greenbean;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GreenLotMapper extends BaseMapper<GreenLot> {
    @Select("SELECT * FROM green_lot WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    GreenLot selectByIdForUpdate(Long id);
}
