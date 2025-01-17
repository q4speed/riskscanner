package io.riskscanner.service;

import io.riskscanner.base.domain.Plugin;
import io.riskscanner.base.domain.PluginExample;
import io.riskscanner.base.mapper.PluginMapper;
import io.riskscanner.commons.exception.RSException;
import io.riskscanner.commons.utils.LogUtil;
import io.riskscanner.commons.utils.ReadFileUtils;
import io.riskscanner.controller.request.Plugin.PluginRequest;
import io.riskscanner.i18n.Translator;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author maguohao
 */
@Service
public class PluginService {

    private static final String BASE_CREDENTIAL_DIC = "support/credential/";
    private static final String JSON_EXTENSION = ".json";

    @Resource
    private PluginMapper pluginMapper;

    public List<Plugin> getAllPlugin() {
        return pluginMapper.selectByExample(null);
    }

    public String getCredential(String pluginId) {
        try {
            return ReadFileUtils.readConfigFile(BASE_CREDENTIAL_DIC, pluginId, JSON_EXTENSION);
        } catch (RSException e) {
            LogUtil.error("Error getting credential parameters: " + pluginId, e);
            RSException.throwException(Translator.get("i18n_ex_plugin_get"));
        } catch (Exception e) {
            LogUtil.error("Error getting credential parameters: " + pluginId, e);
            RSException.throwException(Translator.get("i18n_ex_plugin_get"));
        }
        return Translator.get("i18n_ex_plugin_get");
    }

    public List<Plugin> getPluginList(PluginRequest request) {
        PluginExample example = new PluginExample();
        example.createCriteria().andNameEqualTo(request.getName());
        return pluginMapper.selectByExample(example);
    }

}
