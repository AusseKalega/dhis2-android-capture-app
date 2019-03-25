package org.dhis2.usescases.datasets.dataSetTable;

import org.dhis2.usescases.general.AbstractActivityContracts;
import org.hisp.dhis.android.core.category.CategoryOptionCombo;
import org.hisp.dhis.android.core.dataelement.DataElement;
import org.hisp.dhis.android.core.dataset.DataSet;

import java.util.List;
import java.util.Map;

public class DataSetTableContract {

    public interface View extends AbstractActivityContracts.View {

        void setDataElements(Map<String, List<DataElement>> data, Map<String, List<CategoryOptionCombo>> stringListMap);

        void setDataSet(DataSet data);
    }

    public interface Presenter extends AbstractActivityContracts.Presenter {
        void onBackClick();

        void init(View view, String orgUnitUid, String periodTypeName, String periodInitialDate, String catCombo);

        List<DataElement> getDataElements(String string);

        List<CategoryOptionCombo> getCatOptionCombos(String string);
    }
}
