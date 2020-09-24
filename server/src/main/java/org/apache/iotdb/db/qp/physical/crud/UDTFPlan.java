/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.qp.physical.crud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.iotdb.db.exception.query.QueryProcessException;
import org.apache.iotdb.db.qp.logical.Operator;
import org.apache.iotdb.db.query.udf.core.context.UDFContext;
import org.apache.iotdb.db.query.udf.core.executor.UDTFExecutor;

public class UDTFPlan extends RawDataQueryPlan implements UDFPlan {

  protected Map<String, UDTFExecutor> columnName2Executor = new HashMap<>();
  protected Map<Integer, UDTFExecutor> originalOutputColumnIndex2Executor = new HashMap<>();

  protected List<String> datasetOutputColumnIndex2UdfColumnName = new ArrayList<>();
  protected List<String> datasetOutputColumnIndex2RawQueryColumnName = new ArrayList<>();

  protected Map<String, Integer> pathNameToReaderIndex;

  public UDTFPlan() {
    super();
    setOperatorType(Operator.OperatorType.UDTF);
  }

  @Override
  public void constructUdfExecutors(List<UDFContext> udfContexts) throws QueryProcessException {
    for (int i = 0; i < udfContexts.size(); ++i) {
      UDFContext context = udfContexts.get(i);
      if (context == null) {
        continue;
      }

      String columnName = context.getColumnName();
      if (!columnName2Executor.containsKey(columnName)) {
        UDTFExecutor executor = new UDTFExecutor(context);
        columnName2Executor.put(columnName, executor);
      }
      originalOutputColumnIndex2Executor.put(i, columnName2Executor.get(columnName));
    }
  }

  @Override
  public void initializeUdfExecutor(long queryId) throws QueryProcessException {
    for (UDTFExecutor executor : columnName2Executor.values()) {
      executor.initCollector(queryId);
    }
  }

  @Override
  public void finalizeUDFExecutors() {
    for (UDTFExecutor executor : columnName2Executor.values()) {
      executor.beforeDestroy();
    }
  }

  public UDTFExecutor getExecutorByOriginalOutputColumnIndex(int originalOutputColumn) {
    return originalOutputColumnIndex2Executor.get(originalOutputColumn);
  }

  public UDTFExecutor getExecutorByDataSetOutputColumnIndex(int datasetOutputIndex) {
    return columnName2Executor.get(datasetOutputColumnIndex2UdfColumnName.get(datasetOutputIndex));
  }

  public String getRawQueryColumnNameByDatasetOutputColumnIndex(int datasetOutputIndex) {
    return datasetOutputColumnIndex2RawQueryColumnName.get(datasetOutputIndex);
  }

  public boolean isUdfColumn(int datasetOutputIndex) {
    return datasetOutputColumnIndex2UdfColumnName.get(datasetOutputIndex) != null;
  }

  public int getReaderIndex(String pathName) {
    return pathNameToReaderIndex.get(pathName);
  }

  public void addUdfOutputColumn(String udfDatasetOutputColumn) {
    datasetOutputColumnIndex2UdfColumnName.add(udfDatasetOutputColumn);
    datasetOutputColumnIndex2RawQueryColumnName.add(null);
  }

  public void addRawQueryOutputColumn(String rawQueryOutputColumn) {
    datasetOutputColumnIndex2UdfColumnName.add(null);
    datasetOutputColumnIndex2RawQueryColumnName.add(rawQueryOutputColumn);
  }

  public void setPathNameToReaderIndex(Map<String, Integer> pathNameToReaderIndex) {
    this.pathNameToReaderIndex = pathNameToReaderIndex;
  }
}