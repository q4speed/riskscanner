package io.riskscanner.service;

import io.riskscanner.base.domain.*;
import io.riskscanner.base.mapper.MessageOrderItemMapper;
import io.riskscanner.base.mapper.MessageOrderMapper;
import io.riskscanner.commons.constants.NoticeConstants;
import io.riskscanner.commons.utils.UUIDUtil;
import io.riskscanner.message.MessageDetail;
import io.riskscanner.base.mapper.MessageTaskMapper;
import io.riskscanner.commons.exception.RSException;
import io.riskscanner.i18n.Translator;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class NoticeService {
    @Resource
    private MessageTaskMapper messageTaskMapper;
    @Resource
    private MessageOrderMapper messageOrderMapper;
    @Resource
    private MessageOrderItemMapper messageOrderItemMapper;

    public void saveMessageTask(MessageDetail messageDetail) {
        MessageTaskExample example = new MessageTaskExample();
        example.createCriteria().andIdentificationEqualTo(messageDetail.getIdentification());
        List<MessageTask> messageTaskLists = messageTaskMapper.selectByExample(example);
        if (!messageTaskLists.isEmpty()) {
            delMessage(messageDetail.getIdentification());
        }
        long time = System.currentTimeMillis();
        String identification = messageDetail.getIdentification();
        if (StringUtils.isBlank(identification)) {
            identification = UUID.randomUUID().toString();
        }
        for (String userId : messageDetail.getUserIds()) {
            checkUserIdExist(userId, messageDetail);
            MessageTask messageTask = new MessageTask();
            messageTask.setEvent(messageDetail.getEvent());
            messageTask.setTaskType(messageDetail.getTaskType());
            messageTask.setUserId(userId);
            messageTask.setType(messageDetail.getType());
            messageTask.setIdentification(identification);
            messageTask.setIsSet(false);
            messageTask.setCreateTime(time);
            setTemplate(messageDetail, messageTask);
            messageTaskMapper.insertSelective(messageTask);
        }
    }

    private void setTemplate(MessageDetail messageDetail, MessageTask messageTask) {
        if (StringUtils.isNotBlank(messageDetail.getTemplate())) {
            messageTask.setTemplate(messageDetail.getTemplate());
        }
    }

    private void checkUserIdExist(String userId, MessageDetail list) {
        MessageTaskExample example = new MessageTaskExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andEventEqualTo(list.getEvent())
                .andTypeEqualTo(list.getType())
                .andTaskTypeEqualTo(list.getTaskType());
        if (messageTaskMapper.countByExample(example) > 0) {
            RSException.throwException(Translator.get("message_task_already_exists"));
        }
    }

    public List<MessageDetail> searchMessageByResourceId(String resourceId) {
        MessageTaskExample example = new MessageTaskExample();
        List<MessageTask> messageTaskLists = messageTaskMapper.selectByExampleWithBLOBs(example);
        List<MessageDetail> scheduleMessageTask = new ArrayList<>();
        Map<String, List<MessageTask>> MessageTaskMap = messageTaskLists.stream().collect(Collectors.groupingBy(MessageTask::getIdentification));
        MessageTaskMap.forEach((k, v) -> {
            MessageDetail messageDetail = getMessageDetail(v);
            scheduleMessageTask.add(messageDetail);
        });
        scheduleMessageTask.sort(Comparator.comparing(MessageDetail::getCreateTime, Comparator.nullsLast(Long::compareTo)).reversed());
        return scheduleMessageTask;
    }

    public List<MessageDetail> searchMessageByType(String type) {
        List<MessageDetail> messageDetails = new ArrayList<>();

        MessageTaskExample example = new MessageTaskExample();
        example.createCriteria().andTaskTypeEqualTo(type);
        List<MessageTask> messageTaskLists = messageTaskMapper.selectByExampleWithBLOBs(example);

        Map<String, List<MessageTask>> messageTaskMap = messageTaskLists.stream()
                .collect(Collectors.groupingBy(NoticeService::fetchGroupKey));
        messageTaskMap.forEach((k, v) -> {
            MessageDetail messageDetail = getMessageDetail(v);
            messageDetails.add(messageDetail);
        });

        return messageDetails.stream()
                .sorted(Comparator.comparing(MessageDetail::getCreateTime, Comparator.nullsLast(Long::compareTo)).reversed())
                .collect(Collectors.toList())
                .stream()
                .distinct()
                .collect(Collectors.toList());
    }

    private MessageDetail getMessageDetail(List<MessageTask> messageTasks) {
        Set<String> userIds = new HashSet<>();

        MessageDetail messageDetail = new MessageDetail();
        for (MessageTask m : messageTasks) {
            userIds.add(m.getUserId());
            messageDetail.setEvent(m.getEvent());
            messageDetail.setTaskType(m.getTaskType());
            messageDetail.setIdentification(m.getIdentification());
            messageDetail.setType(m.getType());
            messageDetail.setIsSet(m.getIsSet());
            messageDetail.setCreateTime(m.getCreateTime());
            messageDetail.setTemplate(m.getTemplate());
        }
        if (CollectionUtils.isNotEmpty(userIds)) {
            messageDetail.setUserIds(new ArrayList<>(userIds));
        }
        return messageDetail;
    }

    private static String fetchGroupKey(MessageTask messageTask) {
        return messageTask.getTaskType() + "#" + messageTask.getIdentification();
    }

    public int delMessage(String identification) {
        MessageTaskExample example = new MessageTaskExample();
        example.createCriteria().andIdentificationEqualTo(identification);
        return messageTaskMapper.deleteByExample(example);
    }

    public String createMessageOrder (MessageTask messageTask, Account account) {
        MessageOrder messageOrder = new MessageOrder();
        String uuid = UUIDUtil.newUUID();
        messageOrder.setId(uuid);
        messageOrder.setAccountId(account.getId());
        messageOrder.setAccountName(account.getName());
        messageOrder.setCreateTime(System.currentTimeMillis());
        messageOrder.setMessageTaskId(messageTask.getId());
        messageOrder.setStatus(NoticeConstants.MessageOrderStatus.PROCESSING);
        messageOrderMapper.insertSelective(messageOrder);
        return uuid;
    }

    public void finishMessageOrder (MessageOrder messageOrder) {
        messageOrder.setSendTime(System.currentTimeMillis());
        messageOrder.setStatus(NoticeConstants.MessageOrderStatus.FINISHED);
        messageOrderMapper.updateByPrimaryKeySelective(messageOrder);
    }

    public void createMessageOrderItem (String messageOrderId, Task task) {
        MessageOrderItem messageOrderItem = new MessageOrderItem();
        messageOrderItem.setMessageOrderId(messageOrderId);
        messageOrderItem.setTaskId(task.getId());
        messageOrderItem.setTaskName(task.getTaskName());
        messageOrderItem.setCreateTime(System.currentTimeMillis());
        messageOrderItem.setStatus(NoticeConstants.MessageOrderStatus.PROCESSING);
        messageOrderItemMapper.insertSelective(messageOrderItem);
    }

    public void finishMessageOrderItem (MessageOrderItem messageOrderItem) {
        messageOrderItem.setSendTime(System.currentTimeMillis());
        messageOrderItem.setStatus(NoticeConstants.MessageOrderStatus.FINISHED);
        messageOrderItemMapper.updateByPrimaryKeySelective(messageOrderItem);
    }


}
