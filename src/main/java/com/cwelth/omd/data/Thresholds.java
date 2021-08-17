package com.cwelth.omd.data;

import com.cwelth.omd.OMD;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Thresholds {
    public List<ThresholdItem> list = new ArrayList<>();

    public void sortByAmounts()
    {
        list.sort(Comparator.comparingInt(o -> o.amount));
    }

    public void initFromConfig(ForgeConfigSpec.ConfigValue<List<? extends List<String>>> configList)
    {
        list.clear();
        for(List<String> threshold : configList.get())
        {
            boolean isOk = true;
            int amount = 0;
            boolean exact = Boolean.parseBoolean(threshold.get(1));
            String message = threshold.get(2);
            String command = threshold.get(3);

            try {
                amount = Integer.parseInt(threshold.get(0));
            } catch (NumberFormatException nfe)
            {
                OMD.LOGGER.error("Configuration error. " + threshold.get(0) + " is not a number! Should be integer.");
                isOk = false;
            }

            if(!threshold.get(1).equals("true") && !threshold.get(1).equals("false"))
            {
                OMD.LOGGER.error("Configuration error. " + threshold.get(1) + " is not a boolean! Should be \"true\" or \"false\" (Case-Sensitive!).");
                isOk = false;
            }

            if(isOk)
                list.add(new ThresholdItem(amount, exact, message, command));
            else
            {
                OMD.LOGGER.error("Skipping threshold setting for: \"" + threshold.get(0) + "\", \"" + threshold.get(1) + "\", \"" + threshold.get(2) + "\", \"" + threshold.get(3) + "\"");
            }
        }
        sortByAmounts();
    }

    public ThresholdItem getSuitableThreshold(int amount)
    {
        int preferredIndex = -1;
        for(int idx = 0; idx < list.size(); idx++)
        {
            if(list.get(idx).amount == amount)
            {
                preferredIndex = idx;
                if(list.get(idx).exact) {
                    break;
                }
            }
            if(list.get(idx).amount < amount) {
                if(!list.get(idx).exact) {
                    preferredIndex = idx;
                }
            }
        }
        if(preferredIndex == -1)
            return null;
        else
            return list.get(preferredIndex);
    }
}
