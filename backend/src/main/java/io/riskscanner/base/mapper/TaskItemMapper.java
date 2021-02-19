package io.riskscanner.base.mapper;

import io.riskscanner.base.domain.TaskItem;
import io.riskscanner.base.domain.TaskItemExample;
import io.riskscanner.base.domain.TaskItemWithBLOBs;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface TaskItemMapper {
    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table task_item
     *
     * @mbg.generated Tue Jan 19 17:40:09 CST 2021
     */
    long countByExample(TaskItemExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table task_item
     *
     * @mbg.generated Tue Jan 19 17:40:09 CST 2021
     */
    int deleteByExample(TaskItemExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table task_item
     *
     * @mbg.generated Tue Jan 19 17:40:09 CST 2021
     */
    int deleteByPrimaryKey(String id);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table task_item
     *
     * @mbg.generated Tue Jan 19 17:40:09 CST 2021
     */
    int insert(TaskItemWithBLOBs record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table task_item
     *
     * @mbg.generated Tue Jan 19 17:40:09 CST 2021
     */
    int insertSelective(TaskItemWithBLOBs record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table task_item
     *
     * @mbg.generated Tue Jan 19 17:40:09 CST 2021
     */
    List<TaskItemWithBLOBs> selectByExampleWithBLOBs(TaskItemExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table task_item
     *
     * @mbg.generated Tue Jan 19 17:40:09 CST 2021
     */
    List<TaskItem> selectByExample(TaskItemExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table task_item
     *
     * @mbg.generated Tue Jan 19 17:40:09 CST 2021
     */
    TaskItemWithBLOBs selectByPrimaryKey(String id);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table task_item
     *
     * @mbg.generated Tue Jan 19 17:40:09 CST 2021
     */
    int updateByExampleSelective(@Param("record") TaskItemWithBLOBs record, @Param("example") TaskItemExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table task_item
     *
     * @mbg.generated Tue Jan 19 17:40:09 CST 2021
     */
    int updateByExampleWithBLOBs(@Param("record") TaskItemWithBLOBs record, @Param("example") TaskItemExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table task_item
     *
     * @mbg.generated Tue Jan 19 17:40:09 CST 2021
     */
    int updateByExample(@Param("record") TaskItem record, @Param("example") TaskItemExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table task_item
     *
     * @mbg.generated Tue Jan 19 17:40:09 CST 2021
     */
    int updateByPrimaryKeySelective(TaskItemWithBLOBs record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table task_item
     *
     * @mbg.generated Tue Jan 19 17:40:09 CST 2021
     */
    int updateByPrimaryKeyWithBLOBs(TaskItemWithBLOBs record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table task_item
     *
     * @mbg.generated Tue Jan 19 17:40:09 CST 2021
     */
    int updateByPrimaryKey(TaskItem record);
}