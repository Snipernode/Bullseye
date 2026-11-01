package tsd.ttt.converter;

import java.util.List;
import tsd.ttt.service.ConversionService;
import tsd.ttt.utils.Reloadable;

public class Converter implements Reloadable {
    private final ConversionService conversionService;

    public Converter(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public void reload() {
        conversionService.reloadSettings();
        conversionService.scanInstalledPlugins();
    }

    public boolean hasDetectedContentPlugins() {
        return conversionService.hasDetectedContentPlugins();
    }

    public List<String> detectedPluginNames() {
        return conversionService.getDetectedPluginNames();
    }

    public ConversionService.ConversionReport convertDetectedPlugins() {
        return conversionService.convertDetectedPlugins();
    }
}
