// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.document.PositionDataType;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.config.search.vsm.VsmsummaryConfig;

import java.util.*;

/**
 * Vertical streaming matcher summary specification
 *
 * @author bratseth
 */
public class VsmSummary extends Derived implements VsmsummaryConfig.Producer {
    private Map<SummaryField, List<String>> summaryMap = new java.util.LinkedHashMap<>(1);

    public VsmSummary(Search search) {
        derive(search);
    }

    @Override
    protected void derive(Search search) {
        // Use the default class, as it is the superset
        derive(search, search.getSummary("default"));
    }

    private void derive(Search search, DocumentSummary documentSummary) {
        if (documentSummary==null) return;
        for (SummaryField summaryField : documentSummary.getSummaryFields()) {
            List<String> from = toStringList(summaryField.sourceIterator());

            if (doMapField(search, summaryField)) {
                SDField sdField = search.getField(summaryField.getName());
                if (sdField != null && PositionDataType.INSTANCE.equals(sdField.getDataType())) {
                    summaryMap.put(summaryField, Collections.singletonList(summaryField.getName()));
                } else {
                    summaryMap.put(summaryField, from);
                }
            }
        }
    }

    /**
     * Don't include field in map if sources are the same as the struct sub fields for the SDField.
     * But do map if not all do summarying.
     * Don't map if not struct either.
     * @param summaryField a {@link SummaryField}
     */
    private boolean doMapField(Search search, SummaryField summaryField) {
        SDField sdField = search.getField(summaryField.getName());
        SDDocumentType document = search.getDocument();
        if (sdField==null || ((document != null) && (document.getField(summaryField.getName()) == sdField))) {
            return true;
        }
        if (summaryField.getVsmCommand().equals(SummaryField.VsmCommand.FLATTENJUNIPER)) {
            return true;
        }
        if (!sdField.usesStructOrMap()) {
            return !(sdField.getName().equals(summaryField.getName()));
        }
        if (summaryField.getSourceCount()==sdField.getStructFields().size()) {
            for (SummaryField.Source source : summaryField.getSources()) {
                if (!sdField.getStructFields().contains(new SDField(search.getDocument(), source.getName(), sdField.getDataType()))) { // equals() uses just name
                    return true;
                }
                if (sdField.getStructField(source.getName())!=null && !sdField.getStructField(source.getName()).doesSummarying()) {
                    return true;
                }
            }
            // The sources in the summary field are the same as the sub-fields in the SD field.
            // All sub fields do summarying.
            // Don't map.
            return false;
        }
        return true;
    }

    private List<String> toStringList(Iterator i) {
        List<String> ret = new ArrayList<>();
        while (i.hasNext()) {
            ret.add(i.next().toString());
        }
        return ret;
    }

    @Override
    public String getDerivedName() {
        return "vsmsummary";
    }

    @Override
    public void getConfig(VsmsummaryConfig.Builder vB) {
        for (Map.Entry<SummaryField, List<String>> entry : summaryMap.entrySet()) {
            VsmsummaryConfig.Fieldmap.Builder fmB = new VsmsummaryConfig.Fieldmap.Builder().summary(entry.getKey().getName());
            for (String field : entry.getValue()) {
                fmB.document(new VsmsummaryConfig.Fieldmap.Document.Builder().field(field));
            }
            fmB.command(VsmsummaryConfig.Fieldmap.Command.Enum.valueOf(entry.getKey().getVsmCommand().toString()));
            vB.fieldmap(fmB);
        }
    }
    
}
