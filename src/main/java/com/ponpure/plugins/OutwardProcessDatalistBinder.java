package com.ponpure.plugins;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListBinderDefault;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.model.DataListFilterQueryObject;
import org.joget.commons.util.LogUtil;
import org.joget.directory.dao.RoleDao;
import org.joget.directory.dao.UserDao;

public class OutwardProcessDatalistBinder extends DataListBinderDefault {

    private static final String LOG = "OutwardProcessDatalistBinder";
    private static DataListColumn[] columns;

    @Override
    public String getName() {
        return "PonPure Outward Process Datalist Binder";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Outward Process Datalist Binder with pagination and Outward Work Order No filter";
    }

    @Override
    public String getLabel() {
        return "PonPure Outward Process Datalist Binder";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return "[{\"title\":\"Outward Process Datalist Binder\",\"properties\":[{\"name\":\"username\",\"label\":\"Username\",\"type\":\"textfield\",\"required\":\"true\"}]}]";
    }

    @Override
    public String getPrimaryKeyColumnName() {
        return "id";
    }

    @Override
    public DataListColumn[] getColumns() {

        if (columns == null) {

            Collection<DataListColumn> list = new ArrayList<DataListColumn>();

            list.add(new DataListColumn("id", "ID", true));
            list.add(new DataListColumn("c_billingOnly", "Billing Only", true));
            list.add(new DataListColumn("c_backToBack", "Back To Back", true));
            list.add(new DataListColumn("c_outwardWorkOrderNo", "Outward Doc. No.", true));
            list.add(new DataListColumn("c_invOrg", "Inventory Org", true));
            list.add(new DataListColumn("dateCreated", "Creation Date", true));
            list.add(new DataListColumn("c_requestor", "Requestor", true));
            list.add(new DataListColumn("c_completedStatus", "Completed Status", true));
            list.add(new DataListColumn("c_pendingStatus", "Pending Status", true));
            list.add(new DataListColumn("c_pendingWith", "Pending With", true));
            list.add(new DataListColumn("c_processCompleteStatus", "Process Status", true));
            list.add(new DataListColumn("c_workOrderNo", "Work Order No", true));
            list.add(new DataListColumn("childCompletedStatus", "Child Completed Status", true));
            list.add(new DataListColumn("child_pendingWith", "Child Pending With", true));
            list.add(new DataListColumn("c_processId", "Process ID", true));
            list.add(new DataListColumn("c_supervisor", "Supervisor", true));
            list.add(new DataListColumn("c_vehicleNo", "Vehicle No", true));
            list.add(new DataListColumn("c_vehicleType", "Vehicle Type", true));

            columns = list.toArray(new DataListColumn[]{});
        }

        return columns;
    }

    @Override
    public DataListCollection getData(
            DataList dataList,
            Map properties,
            DataListFilterQueryObject[] filterQueryObjects,
            String sort,
            Boolean desc,
            Integer start,
            Integer rows) {

        DataListCollection data = new DataListCollection();

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {

            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
            con = ds.getConnection();

            String currentUser = getUsername(properties);
            String outwardWorkOrderNo = getOutwardWorkOrderNoFilter(filterQueryObjects);
            String processCompleteStatus = getSingleFilterValue(filterQueryObjects, "c_processCompleteStatus");
            String dateCreated = getSingleFilterValue(filterQueryObjects, "dateCreated");
            String vehicleNo = getSingleFilterValue(filterQueryObjects, "c_vehicleNo");
            String[] vehicleTypes = getMultiFilterValues(filterQueryObjects, "c_vehicleType");

            int offset = start != null && start.intValue() >= 0 ? start.intValue() : 0;
            int pageSize = rows != null && rows.intValue() > 0 ? rows.intValue() : 10;

            String sql = "SELECT wa.id AS id, wa.c_billingOnly, wa.c_backToBack, wa.c_outwardWorkOrderNo, wa.c_invOrg, wa.dateCreated, wa.c_requestor, wa.c_completedStatus, wa.c_pendingStatus, wa.c_pendingWith, wa.c_processCompleteStatus, child_wa.c_workOrderNo AS c_workOrderNo, child_wa.c_completedStatus AS childCompletedStatus, child_wa.c_pendingWith AS child_pendingWith, wp.c_processId, wp.c_supervisor, vl.c_vehicleNo, vl.c_vehicleType FROM app_fd_wms_outward_approval wa LEFT JOIN app_fd_wms_outward_approval child_wa ON child_wa.c_apply_id = wa.id LEFT JOIN app_fd_vehicle_log vl ON vl.id = wa.c_vehicleForOutward OUTER APPLY (SELECT TOP (1) product.c_processId, product.c_supervisor FROM app_fd_wms_outward_product product WHERE product.c_processId = child_wa.id ORDER BY product.id) wp WHERE wa.c_processCompleteStatus IS NOT NULL AND (wa.c_apply_id IS NULL OR wa.c_apply_id = 'null') AND wa.c_requestor = ? ";

            if (outwardWorkOrderNo != null && !outwardWorkOrderNo.isEmpty()) {
                sql += "AND wa.c_outwardWorkOrderNo LIKE ? ";
            }

            if (processCompleteStatus != null && !processCompleteStatus.isEmpty()) {
                sql += "AND wa.c_processCompleteStatus = ? ";
            }

            if (dateCreated != null && !dateCreated.isEmpty()) {
                sql += "AND CAST(wa.dateCreated AS date) = ? ";
            }

            if (vehicleNo != null && !vehicleNo.isEmpty()) {
                sql += "AND vl.c_vehicleNo LIKE ? ";
            }

            if (vehicleTypes != null && vehicleTypes.length > 0) {

                sql += "AND vl.c_vehicleType IN (";

                for (int i = 0; i < vehicleTypes.length; i++) {

                    if (i > 0) {
                        sql += ",";
                    }

                    sql += "?";
                }

                sql += ") ";
            }

            sql += "ORDER BY wa.dateCreated DESC, wa.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

            LogUtil.info(
                    LOG,
                    "[GET-DATA]"
                            + " | user=[" + currentUser + "]"
                            + " | outwardWorkOrderNo=[" + outwardWorkOrderNo + "]"
                            + " | start=" + offset
                            + " | rows=" + pageSize
            );

            LogUtil.info(
                    LOG,
                    "[GET-DATA-SQL] " + sql
            );

            ps = con.prepareStatement(sql);

            int parameterIndex = 1;

            ps.setString(parameterIndex++, currentUser);

            if (outwardWorkOrderNo != null && !outwardWorkOrderNo.isEmpty()) {
                ps.setString(parameterIndex++, "%" + outwardWorkOrderNo + "%");
            }

            if (processCompleteStatus != null && !processCompleteStatus.isEmpty()) {
                ps.setString(parameterIndex++, processCompleteStatus);
            }

            if (dateCreated != null && !dateCreated.isEmpty()) {
                String cleanDate = dateCreated.trim();

                LogUtil.info(
                        LOG,
                        "[DATE-FILTER]"
                                + " | raw=[" + dateCreated + "]"
                                + " | clean=[" + cleanDate + "]"
                );

                java.sql.Date sqlDate = java.sql.Date.valueOf(cleanDate);

                ps.setDate(parameterIndex++, sqlDate);
            }

            if (vehicleNo != null && !vehicleNo.isEmpty()) {
                ps.setString(parameterIndex++, "%" + vehicleNo + "%");
            }

            if (vehicleTypes != null && vehicleTypes.length > 0) {

                for (int i = 0; i < vehicleTypes.length; i++) {
                    ps.setString(parameterIndex++, vehicleTypes[i]);
                }
            }

            ps.setInt(parameterIndex++, offset);
            ps.setInt(parameterIndex, pageSize);

            long startTime = System.currentTimeMillis();

            LogUtil.info(
                    LOG,
                    "[GET-DATA-EXECUTE-START]"
            );

            rs = ps.executeQuery();

            LogUtil.info(
                    LOG,
                    "[GET-DATA-EXECUTE-SUCCESS]"
                            + " | elapsedMs="
                            + (System.currentTimeMillis() - startTime)
            );

            ResultSetMetaData metadata = rs.getMetaData();
            int columnCount = metadata.getColumnCount();

            int fetchedRows = 0;

            while (rs.next()) {

                Map row = new LinkedHashMap();

                for (int i = 1; i <= columnCount; i++) {

                    String columnName = metadata.getColumnLabel(i);
                    Object value = rs.getObject(i);

                    row.put(columnName, value);
                }

                data.add(row);
                fetchedRows++;
            }

            LogUtil.info(
                    LOG,
                    "[GET-DATA-COMPLETE]"
                            + " | fetchedRows=" + fetchedRows
                            + " | totalElapsedMs="
                            + (System.currentTimeMillis() - startTime)
            );

        } catch (Exception e) {

            LogUtil.error(
                    LOG,
                    e,
                    "Error loading outward process datalist"
            );

        } finally {

            closeResources(rs, ps, con);
        }

        return data;
    }

    @Override
    public int getDataTotalRowCount(
            DataList dataList,
            Map properties,
            DataListFilterQueryObject[] filterQueryObjects) {




        UserDao userDao = (UserDao)
                AppUtil.getApplicationContext().getBean("userDao");


        RoleDao roleDao = (RoleDao)
                AppUtil.getApplicationContext().getBean("roleDao");

        roleDao.getRole("ROLE_USER");



        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {

            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
            con = ds.getConnection();

            String currentUser = getUsername(properties);
            String outwardWorkOrderNo = getOutwardWorkOrderNoFilter(filterQueryObjects);
            String processCompleteStatus = getSingleFilterValue(filterQueryObjects, "c_processCompleteStatus");
            String dateCreated = getSingleFilterValue(filterQueryObjects, "dateCreated");
            String vehicleNo = getSingleFilterValue(filterQueryObjects, "c_vehicleNo");
            String[] vehicleTypes = getMultiFilterValues(filterQueryObjects, "c_vehicleType");

//            String sql = "SELECT COUNT(*) "
//                    + "FROM app_fd_wms_outward_approval wa "
//                    + "INNER JOIN app_fd_wms_outward_approval child_wa ON child_wa.c_apply_id = wa.id "
//                    + "WHERE wa.c_processCompleteStatus IS NOT NULL "
//                    + "AND (wa.c_apply_id IS NULL OR wa.c_apply_id = 'null') "
//                    + "AND wa.c_requestor = ? ";


            String sql = "SELECT COUNT(*) "
                    + "FROM app_fd_wms_outward_approval wa "
                    + "LEFT JOIN app_fd_wms_outward_approval child_wa ON child_wa.c_apply_id = wa.id "
                    + "LEFT JOIN app_fd_vehicle_log vl ON vl.id = wa.c_vehicleForOutward "
                    + "WHERE wa.c_processCompleteStatus IS NOT NULL "
                    + "AND (wa.c_apply_id IS NULL OR wa.c_apply_id = 'null') "
                    + "AND wa.c_requestor = ? ";

            if (outwardWorkOrderNo != null && !outwardWorkOrderNo.isEmpty()) {
                sql += "AND wa.c_outwardWorkOrderNo LIKE ? ";
            }

            if (processCompleteStatus != null && !processCompleteStatus.isEmpty()) {
                sql += "AND wa.c_processCompleteStatus = ? ";
            }

            if (dateCreated != null && !dateCreated.isEmpty()) {
                sql += "AND CAST(wa.dateCreated AS date) = ? ";
            }

            if (vehicleNo != null && !vehicleNo.isEmpty()) {
                sql += "AND vl.c_vehicleNo LIKE ? ";
            }

            if (vehicleTypes != null && vehicleTypes.length > 0) {

                sql += "AND vl.c_vehicleType IN (";

                for (int i = 0; i < vehicleTypes.length; i++) {

                    if (i > 0) {
                        sql += ",";
                    }

                    sql += "?";
                }

                sql += ") ";
            }

            LogUtil.info(
                    LOG,
                    "[COUNT-SQL] " + sql
            );

            LogUtil.info(
                    LOG,
                    "[COUNT-PARAMS]"
                            + " | currentUser=[" + currentUser + "]"
                            + " | outwardWorkOrderNo=[" + outwardWorkOrderNo + "]"
            );

            ps = con.prepareStatement(sql);

            int parameterIndex = 1;

            ps.setString(parameterIndex++, currentUser);

            if (outwardWorkOrderNo != null && !outwardWorkOrderNo.isEmpty()) {
                ps.setString(parameterIndex++, "%" + outwardWorkOrderNo + "%");
            }

            if (processCompleteStatus != null && !processCompleteStatus.isEmpty()) {
                ps.setString(parameterIndex++, processCompleteStatus);
            }

            if (dateCreated != null && !dateCreated.isEmpty()) {
                String cleanDate = dateCreated.trim();

                LogUtil.info(
                        LOG,
                        "[DATE-FILTER]"
                                + " | raw=[" + dateCreated + "]"
                                + " | clean=[" + cleanDate + "]"
                );

                java.sql.Date sqlDate = java.sql.Date.valueOf(cleanDate);

                ps.setDate(parameterIndex++, sqlDate);
            }

            if (vehicleNo != null && !vehicleNo.isEmpty()) {
                ps.setString(parameterIndex++, "%" + vehicleNo + "%");
            }

            if (vehicleTypes != null && vehicleTypes.length > 0) {

                for (int i = 0; i < vehicleTypes.length; i++) {
                    ps.setString(parameterIndex++, vehicleTypes[i]);
                }
            }

            long startTime = System.currentTimeMillis();

            LogUtil.info(
                    LOG,
                    "[COUNT-EXECUTE-START]"
            );

            LogUtil.info(
                    LOG,
                    "[FILTERS]"
                            + " | outwardWorkOrderNo=[" + outwardWorkOrderNo + "]"
                            + " | processCompleteStatus=[" + processCompleteStatus + "]"
                            + " | dateCreated=[" + dateCreated + "]"
                            + " | vehicleNo=[" + vehicleNo + "]"
                            + " | vehicleTypes=[" + java.util.Arrays.toString(vehicleTypes) + "]"
            );

            rs = ps.executeQuery();

            LogUtil.info(
                    LOG,
                    "[COUNT-EXECUTE-SUCCESS]"
                            + " | elapsedMs="
                            + (System.currentTimeMillis() - startTime)
            );

            if (rs.next()) {

                int total = rs.getInt(1);

                LogUtil.info(
                        LOG,
                        "[TOTAL-COUNT]"
                                + " | user=[" + currentUser + "]"
                                + " | outwardWorkOrderNo=[" + outwardWorkOrderNo + "]"
                                + " | total=" + total
                                + " | totalElapsedMs="
                                + (System.currentTimeMillis() - startTime)
                );

                return total;
            }

            LogUtil.info(
                    LOG,
                    "[TOTAL-COUNT] No count row returned"
            );

        } catch (Exception e) {

            LogUtil.error(
                    LOG,
                    e,
                    "Error getting outward process total count"
            );

        } finally {

            closeResources(rs, ps, con);
        }

        return 0;
    }

    private String getUsername(Map properties) {

        if (properties == null) {
            LogUtil.info(LOG, "[USERNAME-PROPERTY] properties is null");
            return "";
        }

        Object value = properties.get("username");

        if (value == null) {
            LogUtil.info(LOG, "[USERNAME-PROPERTY] username property is null");
            return "";
        }

        String username = value.toString().trim();

        LogUtil.info(
                LOG,
                "[USERNAME-PROPERTY] username=[" + username + "]"
        );

        return username;
    }

    private String getOutwardWorkOrderNoFilter(
            DataListFilterQueryObject[] filterQueryObjects) {

        if (filterQueryObjects == null) {
            return null;
        }

        for (int i = 0; i < filterQueryObjects.length; i++) {

            DataListFilterQueryObject filter = filterQueryObjects[i];

            if (filter == null) {
                continue;
            }

            String query = filter.getQuery();
            String[] values = filter.getValues();

            if (query != null
                    && query.indexOf("c_outwardWorkOrderNo") >= 0
                    && values != null
                    && values.length > 0
                    && values[0] != null) {

                String value = values[0].trim().replace("%", "");

                if (!value.isEmpty()) {
                    return value;
                }
            }
        }

        return null;
    }

    private void closeResources(
            ResultSet rs,
            PreparedStatement ps,
            Connection con) {

        try {

            if (rs != null) {
                rs.close();
            }

        } catch (Exception e) {

            LogUtil.error(
                    LOG,
                    e,
                    "Error closing ResultSet"
            );
        }

        try {

            if (ps != null) {
                ps.close();
            }

        } catch (Exception e) {

            LogUtil.error(
                    LOG,
                    e,
                    "Error closing PreparedStatement"
            );
        }

        try {

            if (con != null) {
                con.close();
            }

        } catch (Exception e) {

            LogUtil.error(
                    LOG,
                    e,
                    "Error closing Connection"
            );
        }
    }

    private String getSingleFilterValue(DataListFilterQueryObject[] filterQueryObjects, String fieldName) {

        if (filterQueryObjects == null) {
            return null;
        }

        for (int i = 0; i < filterQueryObjects.length; i++) {

            DataListFilterQueryObject filter = filterQueryObjects[i];

            if (filter == null) {
                continue;
            }

            String query = filter.getQuery();
            String[] values = filter.getValues();

            if (query != null
                    && query.indexOf(fieldName) >= 0
                    && values != null
                    && values.length > 0
                    && values[0] != null) {

                String value = values[0].trim().replace("%", "");

                if (!value.isEmpty()) {
                    return value;
                }
            }
        }

        return null;
    }

    private String[] getMultiFilterValues(DataListFilterQueryObject[] filterQueryObjects, String fieldName) {

        if (filterQueryObjects == null) {
            return new String[0];
        }

        for (int i = 0; i < filterQueryObjects.length; i++) {

            DataListFilterQueryObject filter = filterQueryObjects[i];

            if (filter == null) {
                continue;
            }

            String query = filter.getQuery();
            String[] values = filter.getValues();

            if (query != null
                    && query.indexOf(fieldName) >= 0
                    && values != null
                    && values.length > 0) {

                ArrayList<String> cleanValues = new ArrayList<String>();

                for (int j = 0; j < values.length; j++) {

                    if (values[j] == null) {
                        continue;
                    }

                    String rawValue = values[j].trim().replace("%", "");

                    if (rawValue.isEmpty()) {
                        continue;
                    }

                    String[] splitValues = rawValue.split("[,;]");

                    for (int k = 0; k < splitValues.length; k++) {

                        String value = splitValues[k].trim();

                        if (!value.isEmpty() && !cleanValues.contains(value)) {
                            cleanValues.add(value);
                        }
                    }
                }

                return cleanValues.toArray(new String[0]);
            }
        }

        return new String[0];
    }
}