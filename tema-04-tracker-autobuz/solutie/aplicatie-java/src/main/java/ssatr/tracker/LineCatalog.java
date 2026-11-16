package ssatr.tracker;

import java.util.List;

import ssatr.tracker.Model.Line;

/** De unde vin liniile și stațiile: API-ul real (ApiClient) sau simularea (SimCatalog). */
public interface LineCatalog {

    /** toate liniile, cu sensurile lor, fără stații */
    List<Line> lines() throws Exception;

    /** o linie, cu stațiile fiecărui sens în ordinea parcurgerii */
    Line line(int routeId) throws Exception;
}