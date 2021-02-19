package io.riskscanner.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import io.riskscanner.base.domain.*;
import io.riskscanner.base.mapper.*;
import io.riskscanner.base.mapper.ext.ExtRuleGroupMapper;
import io.riskscanner.base.mapper.ext.ExtRuleMapper;
import io.riskscanner.base.mapper.ext.ExtRuleTagMapper;
import io.riskscanner.base.mapper.ext.ExtRuleTypeMapper;
import io.riskscanner.base.rs.SelectTag;
import io.riskscanner.commons.constants.CloudAccountConstants;
import io.riskscanner.commons.constants.ResourceOperation;
import io.riskscanner.commons.constants.ResourceTypeConstants;
import io.riskscanner.commons.exception.RSException;
import io.riskscanner.commons.utils.*;
import io.riskscanner.controller.request.rule.CreateRuleRequest;
import io.riskscanner.controller.request.rule.RuleGroupRequest;
import io.riskscanner.controller.request.rule.RuleTagRequest;
import io.riskscanner.dto.QuartzTaskDTO;
import io.riskscanner.dto.RuleDTO;
import io.riskscanner.dto.RuleGroupDTO;
import io.riskscanner.dto.RuleTagDTO;
import io.riskscanner.i18n.Translator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author maguohao
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class RuleService {

    @Resource
    private RuleMapper ruleMapper;
    @Resource
    private RuleTagMapper ruleTagMapper;
    @Resource
    private RuleTagMappingMapper ruleTagMappingMapper;
    @Resource
    private PluginMapper pluginMapper;
    @Resource
    private ExtRuleMapper extRuleMapper;
    @Resource
    private RuleTypeMapper ruleTypeMapper;
    @Resource
    private ExtRuleTagMapper extRuleTagMapper;
    @Resource
    private ExtRuleTypeMapper extRuleTypeMapper;
    @Resource
    private TaskService taskService;
    @Resource
    private ResourceRuleMapper resourceRuleMapper;
    @Resource
    private CommonThreadPool commonThreadPool;
    @Resource
    private AccountMapper accountMapper;
    @Resource
    private AccountService accountService;
    @Resource
    private RuleGroupMapper ruleGroupMapper;
    @Resource
    private RuleGroupMappingMapper ruleGroupMappingMapper;
    @Resource
    private RuleInspectionReportMapper ruleInspectionReportMapper;
    @Resource
    private RuleInspectionReportMappingMapper ruleInspectionReportMappingMapper;
    @Resource
    private OrderService orderService;
    @Resource
    private ScanHistoryMapper scanHistoryMapper;
    @Resource
    private ExtRuleGroupMapper extRuleGroupMapper;
    @Resource
    private TaskItemMapper taskItemMapper;
    @Resource
    private ScanTaskHistoryMapper scanTaskHistoryMapper;

    public List<RuleDTO> getRules(CreateRuleRequest ruleRequest) {
        List<RuleDTO> ruleDTOS = extRuleMapper.listAllWithTag(ruleRequest);
        return ruleDTOS;
    }

    public List<RuleTag> ruleTagList(RuleTagRequest request) {
        List<RuleTag> ruleTagList = extRuleTagMapper.list(request);
        return ruleTagList;
    }

    public List<RuleGroupDTO> ruleGroupList(RuleGroupRequest request) {
        List<RuleGroupDTO> ruleGroupList = extRuleGroupMapper.list(request);
        return ruleGroupList;
    }

    public Rule saveRules(CreateRuleRequest ruleRequest) throws Exception {
        try {
            taskService.validateYaml(BeanUtils.copyBean(new QuartzTaskDTO(), ruleRequest));
            if (StringUtils.isBlank(ruleRequest.getId())) {
                ruleRequest.setId(UUIDUtil.newUUID());
                ruleRequest.setLastModified(System.currentTimeMillis());
                ruleRequest.setType("custodian");

            }
            Rule t = ruleMapper.selectByPrimaryKey(ruleRequest.getId());
            if (t == null) {
                Plugin plugin = pluginMapper.selectByPrimaryKey(ruleRequest.getPluginId());
                ruleRequest.setPluginId(plugin.getId());
                ruleRequest.setPluginName(plugin.getName());
                ruleRequest.setPluginIcon(plugin.getIcon());
                ruleRequest.setFlag(false);
                ruleMapper.insertSelective(ruleRequest);
            } else {
                Plugin plugin = pluginMapper.selectByPrimaryKey(ruleRequest.getPluginId());
                ruleRequest.setPluginId(plugin.getId());
                ruleRequest.setPluginName(plugin.getName());
                ruleRequest.setPluginIcon(plugin.getIcon());
                ruleRequest.setFlag(false);
                ruleRequest.setLastModified(System.currentTimeMillis());
                ruleMapper.updateByPrimaryKeySelective(ruleRequest);
            }
            if (ruleRequest.getTags() != null) {
                List<String> tags = ruleRequest.getTags();
                saveRuleTagMapping(ruleRequest.getId(), tags.toArray(new String[0]));
            } else {
                String[] tagKeys = new String[0];
                tagKeys[0] = ruleRequest.getTagKey();
                saveRuleTagMapping(ruleRequest.getId(), tagKeys);
            }

            saveRuleGroupMapping(ruleRequest.getId(), ruleRequest.getRuleSets());
            saveRuleInspectionReportMapping(ruleRequest.getId(), ruleRequest.getInspectionSeports());
            saveRuleType(ruleRequest);
            OperationLogService.log(SessionUtils.getUser(), ruleRequest.getId(), ruleRequest.getName(), ResourceTypeConstants.RULE.name(), ResourceOperation.CREATE, "创建规则");
        } catch (Exception e) {
            throw e;
        }
        return ruleRequest;
    }

    public boolean saveRuleType(CreateRuleRequest ruleRequest) throws Exception {
        try {
            String script = ruleRequest.getScript();
            JSONArray jsonArray = JSON.parseArray(ruleRequest.getParameter());
            for (Object o : jsonArray) {
                JSONObject jsonObject = (JSONObject) o;
                String key = "${{" + jsonObject.getString("key") + "}}";
                if (script.contains(key)) {
                    script = script.replace(key, jsonObject.getString("defaultValue"));
                }
            }
            Yaml yaml = new Yaml();
            Map map = (Map) yaml.load(script);
            RuleType ruleType = new RuleType();
            ruleType.setRuleId(ruleRequest.getId());
            RuleTypeExample example = new RuleTypeExample();
            example.createCriteria().andRuleIdEqualTo(ruleRequest.getId());
            ruleTypeMapper.deleteByExample(example);
            if (map != null) {
                List<Map> list = (List) map.get("policies");
                for (Map m : list) {
                    String resourceType = m.get("resource").toString();
                    example.createCriteria().andRuleIdEqualTo(ruleRequest.getId()).andResourceTypeEqualTo(resourceType);
                    List<RuleType> ruleTypes = ruleTypeMapper.selectByExample(example);
                    if (ruleTypes.size() == 0) {
                        ruleType.setId(UUIDUtil.newUUID());
                        ruleType.setResourceType(resourceType);
                        ruleTypeMapper.insertSelective(ruleType);
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.error(Translator.get("i18n_compliance_rule_code_error") + ": " + e.getMessage());
            return false;
        }
        return true;
    }

    public Rule copyRule(CreateRuleRequest ruleRequest) throws Exception {
        try {
            taskService.validateYaml(BeanUtils.copyBean(new QuartzTaskDTO(), ruleRequest));
            ruleRequest.setLastModified(System.currentTimeMillis());
            ruleRequest.setId(UUIDUtil.newUUID());
            ruleRequest.setFlag(false);
            ruleMapper.insertSelective(ruleRequest);
            if (ruleRequest.getTags() != null) {
                List<String> tags = ruleRequest.getTags();
                saveRuleTagMapping(ruleRequest.getId(), tags.toArray(new String[0]));
            }
            saveRuleGroupMapping(ruleRequest.getId(), ruleRequest.getRuleSets());
            saveRuleInspectionReportMapping(ruleRequest.getId(), ruleRequest.getInspectionSeports());
            boolean flag = saveRuleType(ruleRequest);
            if (!flag) {
                throw new Exception(Translator.get("i18n_compliance_rule_code_error"));
            }
            OperationLogService.log(SessionUtils.getUser(), ruleRequest.getId(), ruleRequest.getName(), ResourceTypeConstants.RULE.name(), ResourceOperation.CREATE, "复制规则");
        } catch (Exception e) {
            throw e;
        }
        return ruleRequest;
    }

    public void saveRuleTagMapping(String ruleId, String[] tagKeys) {
        deleteRuleTag(null, ruleId);
        if (tagKeys == null || tagKeys.length < 1) {
            return;
        }
        for (String tagKey : tagKeys) {
            RuleTagMapping sfRulesTagMapping = new RuleTagMapping();
            sfRulesTagMapping.setRuleId(ruleId);
            sfRulesTagMapping.setTagKey(tagKey);
            ruleTagMappingMapper.insert(sfRulesTagMapping);
        }
    }

    public void saveRuleGroupMapping(String ruleId, List<String> ruleGroups) {
        deleteRuleGroupMapping(ruleId);
        if (ruleGroups == null || ruleGroups.size() < 1) {
            return;
        }
        for (String ruleGroup : ruleGroups) {
            RuleGroupMapping ruleGroupMapping = new RuleGroupMapping();
            ruleGroupMapping.setRuleId(ruleId);
            ruleGroupMapping.setGroupId(ruleGroup);
            ruleGroupMappingMapper.insertSelective(ruleGroupMapping);
        }
    }

    public void saveRuleInspectionReportMapping(String ruleId, List<String> ruleInspectionReports) {
        deleteRuleInspectionReportMapping(ruleId);
        if (ruleInspectionReports == null || ruleInspectionReports.size() < 1) {
            return;
        }
        for (String ruleInspectionReport : ruleInspectionReports) {
            RuleInspectionReportMapping ruleGroupMapping = new RuleInspectionReportMapping();
            ruleGroupMapping.setRuleId(ruleId);
            ruleGroupMapping.setReportId(ruleInspectionReport);
            ruleInspectionReportMappingMapper.insertSelective(ruleGroupMapping);
        }
    }

    public Object runRules(RuleDTO ruleDTO) throws Exception {
        try {
            taskService.validateYaml(BeanUtils.copyBean(new QuartzTaskDTO(), ruleDTO));
            QuartzTaskDTO quartzTaskDTO = new QuartzTaskDTO();
            BeanUtils.copyBean(quartzTaskDTO, ruleDTO);
            quartzTaskDTO.setType("manual");
            return taskService.saveManualTask(quartzTaskDTO);
        } catch (Exception e) {
            throw e;
        }
    }

    public Object dryRun(RuleDTO ruleDTO) throws Exception {
        QuartzTaskDTO quartzTaskDTO = new QuartzTaskDTO();
        BeanUtils.copyBean(quartzTaskDTO, ruleDTO);
        //validate && dryrun
        return taskService.dryRun(quartzTaskDTO);
    }

    public void deleteRule(String id) throws RSException {
        Rule rule = ruleMapper.selectByPrimaryKey(id);
        ResourceRuleExample resourceItemRuleExample = new ResourceRuleExample();
        resourceItemRuleExample.createCriteria().andRuleIdEqualTo(id);
        List<ResourceRule> list = resourceRuleMapper.selectByExample(resourceItemRuleExample);
        if (list.size() == 0) {
            ruleMapper.deleteByPrimaryKey(id);
            RuleTagMappingExample example = new RuleTagMappingExample();
            example.createCriteria().andRuleIdEqualTo(id);
            ruleTagMappingMapper.deleteByExample(example);
            RuleTypeExample ruleTypeExample = new RuleTypeExample();
            ruleTypeExample.createCriteria().andRuleIdEqualTo(id);
            ruleTypeMapper.deleteByExample(ruleTypeExample);
            deleteRuleInspectionReportMapping(id);
            deleteRuleGroupMapping(id);
            OperationLogService.log(SessionUtils.getUser(), id, rule.getName(), ResourceTypeConstants.RULE.name(), ResourceOperation.DELETE, "删除规则");
        } else {
            throw new RSException(Translator.get("i18n_compliance_rule_useage_error"));
        }
    }

    public RuleDTO getRuleById(String id) {
        RuleDTO ruleDTO = new RuleDTO();
        Rule rule = ruleMapper.selectByPrimaryKey(id);
        BeanUtils.copyBean(ruleDTO, rule);

        //规则标签
        List<String> tags = new LinkedList<>();
        RuleTagMappingExample example = new RuleTagMappingExample();
        example.createCriteria().andRuleIdEqualTo(id);
        List<RuleTagMapping> ruleTagMappingList = ruleTagMappingMapper.selectByExample(example);
        ruleTagMappingList.stream().forEach(obj -> tags.add(obj.getTagKey()));
        ruleDTO.setTags(tags);

        //规则组
        List<Integer> ruleSets = new ArrayList<>();
        RuleGroupMappingExample ruleGroupMappingExample = new RuleGroupMappingExample();
        ruleGroupMappingExample.createCriteria().andRuleIdEqualTo(id);
        List<RuleGroupMapping> ruleGroupMappings = ruleGroupMappingMapper.selectByExample(ruleGroupMappingExample);
        ruleGroupMappings.stream().forEach(obj -> ruleSets.add(Integer.valueOf(obj.getGroupId())));
        ruleDTO.setRuleSets(ruleSets);

        //规则条例
        List<Integer> inspectionSeports = new ArrayList<>();
        RuleInspectionReportMappingExample ruleInspectionReportMappingExample = new RuleInspectionReportMappingExample();
        ruleInspectionReportMappingExample.createCriteria().andRuleIdEqualTo(id);
        List<RuleInspectionReportMapping> ruleInspectionReportMappings = ruleInspectionReportMappingMapper.selectByExample(ruleInspectionReportMappingExample);
        ruleInspectionReportMappings.stream().forEach(obj -> inspectionSeports.add(Integer.valueOf(obj.getReportId())));
        ruleDTO.setInspectionSeports(inspectionSeports);

        return ruleDTO;
    }

    public RuleDTO getRuleByTaskId(String taskId) {
        TaskItemExample taskItemExample = new TaskItemExample();
        taskItemExample.createCriteria().andTaskIdEqualTo(taskId);
        String id = taskItemMapper.selectByExample(taskItemExample).get(0).getRuleId();

        RuleDTO ruleDTO = new RuleDTO();
        Rule rule = ruleMapper.selectByPrimaryKey(id);
        BeanUtils.copyBean(ruleDTO, rule);

        //规则标签
        List<String> tags = new LinkedList<>();
        RuleTagMappingExample example = new RuleTagMappingExample();
        example.createCriteria().andRuleIdEqualTo(id);
        List<RuleTagMapping> ruleTagMappingList = ruleTagMappingMapper.selectByExample(example);
        ruleTagMappingList.stream().forEach(obj -> tags.add(obj.getTagKey()));
        ruleDTO.setTags(tags);

        //规则组
        List<Integer> ruleSets = new ArrayList<>();
        RuleGroupMappingExample ruleGroupMappingExample = new RuleGroupMappingExample();
        ruleGroupMappingExample.createCriteria().andRuleIdEqualTo(id);
        List<RuleGroupMapping> ruleGroupMappings = ruleGroupMappingMapper.selectByExample(ruleGroupMappingExample);
        ruleGroupMappings.stream().forEach(obj -> ruleSets.add(Integer.valueOf(obj.getGroupId())));
        ruleDTO.setRuleSets(ruleSets);

        //规则条例
        List<Integer> inspectionSeports = new ArrayList<>();
        RuleInspectionReportMappingExample ruleInspectionReportMappingExample = new RuleInspectionReportMappingExample();
        ruleInspectionReportMappingExample.createCriteria().andRuleIdEqualTo(id);
        List<RuleInspectionReportMapping> ruleInspectionReportMappings = ruleInspectionReportMappingMapper.selectByExample(ruleInspectionReportMappingExample);
        ruleInspectionReportMappings.stream().forEach(obj -> inspectionSeports.add(Integer.valueOf(obj.getReportId())));
        ruleDTO.setInspectionSeports(inspectionSeports);

        return ruleDTO;
    }

    public boolean getRuleByName(CreateRuleRequest request) {
        RuleExample example = new RuleExample();
        RuleExample.Criteria criteria = example.createCriteria();
        criteria.andNameEqualTo(request.getName());
        List<Rule> list = ruleMapper.selectByExample(example);
        if (StringUtils.equalsIgnoreCase(request.getType(), "edit")) {
            if (list.size() > 1) {
                return false;
            } else if (list.size() == 1) {
                if (!StringUtils.equalsIgnoreCase(request.getId(), list.get(0).getId())) {
                    return false;
                }
            }
        } else {
            if (list.size() > 0) {
                return false;
            }
        }
        return true;
    }

    public List<RuleTagDTO> getRuleTags() {
        RuleTagExample tagExample = new RuleTagExample();
        tagExample.setOrderByClause("_index");
        List<RuleTagDTO> ruleTagDTOList = new LinkedList<>();
        for (RuleTag ruleTag : ruleTagMapper.selectByExample(tagExample)) {
            RuleTagDTO ruleTagDTO = new RuleTagDTO();
            BeanUtils.copyBean(ruleTagDTO, ruleTag);
            ruleTagDTOList.add(ruleTagDTO);
        }
        return ruleTagDTOList;
    }

    public RuleTag saveRuleTag(RuleTag ruleTag) {
        ruleTagMapper.insertSelective(ruleTag);
        return ruleTag;
    }

    public RuleTag updateRuleTag(RuleTag ruleTag) {
        ruleTagMapper.updateByPrimaryKey(ruleTag);
        return ruleTag;
    }

    public int deleteRuleTag(String tagkey, String ruleId) {
        int result = 0;
        if (StringUtils.isNotBlank(tagkey)) {
            result = ruleTagMapper.deleteByPrimaryKey(tagkey);
            RuleTagExample RuleTagExample = new RuleTagExample();
            RuleTagExample.createCriteria().andTagKeyEqualTo(tagkey);
            ruleTagMapper.deleteByExample(RuleTagExample);
        }
        if (StringUtils.isNotBlank(ruleId)) {
            RuleTagMappingExample RuleTagMappingExample = new RuleTagMappingExample();
            RuleTagMappingExample.createCriteria().andRuleIdEqualTo(ruleId);
            ruleTagMappingMapper.deleteByExample(RuleTagMappingExample);
        }
        return result;
    }

    public int deleteRuleGroupMapping(String ruleId) {
        int result = 0;
        if (StringUtils.isNotBlank(ruleId)) {
            RuleGroupMappingExample example = new RuleGroupMappingExample();
            example.createCriteria().andRuleIdEqualTo(ruleId);
            result = ruleGroupMappingMapper.deleteByExample(example);
        }
        return result;
    }

    public int deleteRuleInspectionReportMapping(String ruleId) {
        int result = 0;
        if (StringUtils.isNotBlank(ruleId)) {
            RuleInspectionReportMappingExample example = new RuleInspectionReportMappingExample();
            example.createCriteria().andRuleIdEqualTo(ruleId);
            result = ruleInspectionReportMappingMapper.deleteByExample(example);
        }
        return result;
    }

    public int deleteRuleTagByTagKey(String tagkey) throws Exception {
        RuleTagMappingExample example = new RuleTagMappingExample();
        example.createCriteria().andTagKeyEqualTo(tagkey);
        List<RuleTagMapping> list = ruleTagMappingMapper.selectByExample(example);
        if (list.size() > 0) throw new Exception(Translator.get("i18n_not_allowed"));
        OperationLogService.log(SessionUtils.getUser(), tagkey, tagkey, ResourceTypeConstants.RULE_TAG.name(), ResourceOperation.DELETE, "删除规则标签");
        return ruleTagMapper.deleteByPrimaryKey(tagkey);
    }

    public List<Map<String, String>> getAllResourceTypes() {
        return extRuleTypeMapper.selectByExample();
    }

    public List<RuleGroup> getRuleGroups() {
        return ruleGroupMapper.selectByExample(null);
    }

    public List<RuleInspectionReport> getRuleInspectionReport() {
        return ruleInspectionReportMapper.selectByExample(null);
    }

    public String getResourceTypesById(String ruleId) {
        return extRuleTypeMapper.getResourceTypesById(ruleId);
    }

    public int changeStatus(Rule rule) {
        return ruleMapper.updateByPrimaryKeySelective(rule);
    }

    @Transactional(propagation = Propagation.SUPPORTS, isolation = Isolation.READ_COMMITTED, rollbackFor = {RuntimeException.class, Exception.class})
    public void scan(List<String> ids) throws RSException {
        ids.forEach(id -> {
            AccountWithBLOBs account = accountMapper.selectByPrimaryKey(id);
            this.scan(account);
        });
    }

    @Transactional(propagation = Propagation.SUPPORTS, isolation = Isolation.READ_COMMITTED, rollbackFor = {RuntimeException.class, Exception.class})
    public void reScans(String accountId) throws RSException {
        AccountWithBLOBs account = accountMapper.selectByPrimaryKey(accountId);
        this.scan(account);
    }

    @Transactional(propagation = Propagation.SUPPORTS, isolation = Isolation.READ_COMMITTED, rollbackFor = {RuntimeException.class, Exception.class})
    public void reScan(String taskId, String accountId) throws RSException {
        TaskItemExample example = new TaskItemExample();
        example.createCriteria().andTaskIdEqualTo(taskId);
        List<TaskItem> taskItems = taskItemMapper.selectByExample(example);
        AccountWithBLOBs account = accountMapper.selectByPrimaryKey(accountId);
        RuleDTO rule = getRuleById(taskItems.get(0).getRuleId());
        if (!rule.getStatus()) {
            throw new RSException(Translator.get("i18n_disabled_rules_not_scanning"));
        }
        Integer scanId = orderService.insertScanHistory(account);
        this.dealTask(rule, account, scanId);
    }

    private void scan(AccountWithBLOBs account) {
        Integer scanId = orderService.insertScanHistory(account);

        QuartzTaskDTO dto = new QuartzTaskDTO();
        dto.setAccountId(account.getId());
        dto.setPluginId(account.getPluginId());
        dto.setStatus(true);
        List<RuleDTO> ruleDTOS = accountService.getRules(dto);
        for (RuleDTO rule : ruleDTOS) {
            this.dealTask(rule, account, scanId);
        }
    }

    private void dealTask (RuleDTO rule, AccountWithBLOBs account, Integer scanId) {
        try {
            if (!rule.getStatus()) {
                LogUtil.warn(rule.getName() + ": " + Translator.get("i18n_disabled_rules_not_scanning"));
                return;
            }
            QuartzTaskDTO quartzTaskDTO = new QuartzTaskDTO();
            BeanUtils.copyBean(quartzTaskDTO, rule);
            List<SelectTag> SelectTags = new LinkedList<>();
            SelectTag s = new SelectTag();
            s.setAccountId(account.getId());
            JSONArray array = JSONArray.parseArray(account.getRegions());
            JSONObject object = null;
            List<String> regions = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                object = array.getJSONObject(i);
                String value = object.getString("regionId");
                regions.add(value);
            }
            s.setRegions(regions);
            SelectTags.add(s);
            quartzTaskDTO.setSelectTags(SelectTags);
            quartzTaskDTO.setType("manual");
            quartzTaskDTO.setAccountId(account.getId());
            quartzTaskDTO.setTaskName(rule.getName());
            Task task = taskService.saveManualTask(quartzTaskDTO);
            orderService.insertTaskHistory(task, scanId);
        } catch (java.lang.Exception e) {
            LogUtil.error(e);
        }
    }

    public Integer insertScanHistory (String accountId) {
        return orderService.insertScanHistory(accountMapper.selectByPrimaryKey(accountId));
    }

    public void syncScanHistory () {
        AccountExample accountExample = new AccountExample();
        accountExample.createCriteria().andStatusEqualTo(CloudAccountConstants.Status.VALID.name());
        List<AccountWithBLOBs> accountList = accountMapper.selectByExampleWithBLOBs(accountExample);

        accountList.forEach(account -> {
            try {
                RuleExample ruleExample = new RuleExample();
                ruleExample.createCriteria().andPluginIdEqualTo(account.getPluginId());
                List<Rule> rules = ruleMapper.selectByExample(ruleExample);
                if (rules.size() == 0) return;

                long current = System.currentTimeMillis();
                long zero = current/(1000*3600*24)*(1000*3600*24) - TimeZone.getDefault().getRawOffset();//当天00点

                ScanHistoryExample example = new ScanHistoryExample();
                example.createCriteria().andAccountIdEqualTo(account.getId()).andCreateTimeEqualTo(zero);
                List<ScanHistory> list = scanHistoryMapper.selectByExample(example);
                if (list.size() > 0) {
                    orderService.insertScanHistory(account);
                } else {
                    Integer scanId = orderService.insertScanHistory(account);

                    QuartzTaskDTO dto = new QuartzTaskDTO();
                    dto.setAccountId(account.getId());
                    dto.setPluginId(account.getPluginId());
                    dto.setStatus(true);
                    List<RuleDTO> ruleDTOS = accountService.getRules(dto);
                    for (RuleDTO rule : ruleDTOS) {
                        commonThreadPool.addTask(() -> {
                            try {
                                QuartzTaskDTO quartzTaskDTO = new QuartzTaskDTO();
                                BeanUtils.copyBean(quartzTaskDTO, rule);
                                List<SelectTag> SelectTags = new LinkedList<>();
                                SelectTag s = new SelectTag();
                                s.setAccountId(account.getId());
                                JSONArray array = JSONArray.parseArray(account.getRegions());
                                JSONObject object = null;
                                List<String> regions = new ArrayList<>();
                                for (int i = 0; i < array.size(); i++) {
                                    object = array.getJSONObject(i);
                                    String value = object.getString("regionId");
                                    regions.add(value);
                                }
                                s.setRegions(regions);
                                SelectTags.add(s);
                                quartzTaskDTO.setSelectTags(SelectTags);
                                quartzTaskDTO.setType("manual");
                                quartzTaskDTO.setAccountId(account.getId());
                                quartzTaskDTO.setTaskName(rule.getName());
                                Task task = taskService.saveManualTask(quartzTaskDTO);
                                orderService.insertTaskHistory(task, scanId);
                            } catch (java.lang.Exception e) {
                                LogUtil.error(e);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                LogUtil.error(e.getMessage());
            }
        });
    }

    public RuleGroup saveRuleGroup(RuleGroup ruleGroup) {
        ruleGroupMapper.insertSelective(ruleGroup);
        return ruleGroup;
    }

    public RuleGroup updateRuleGroup(RuleGroup ruleGroup) {
        ruleGroupMapper.updateByPrimaryKey(ruleGroup);
        return ruleGroup;
    }

    public int deleteRuleGroupById(Integer id) {
        int result = ruleGroupMapper.deleteByPrimaryKey(id);
        return result;
    }
}