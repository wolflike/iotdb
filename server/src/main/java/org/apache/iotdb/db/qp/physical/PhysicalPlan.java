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
package org.apache.iotdb.db.qp.physical;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import org.apache.iotdb.db.exception.metadata.IllegalPathException;
import org.apache.iotdb.db.exception.query.QueryProcessException;
import org.apache.iotdb.db.metadata.PartialPath;
import org.apache.iotdb.db.qp.logical.Operator;
import org.apache.iotdb.db.qp.logical.Operator.OperatorType;
import org.apache.iotdb.db.qp.physical.crud.DeletePlan;
import org.apache.iotdb.db.qp.physical.sys.AlterTimeSeriesPlan;
import org.apache.iotdb.db.qp.physical.crud.InsertRowPlan;
import org.apache.iotdb.db.qp.physical.crud.InsertTabletPlan;
import org.apache.iotdb.db.qp.physical.sys.AuthorPlan;
import org.apache.iotdb.db.qp.physical.sys.CreateTimeSeriesPlan;
import org.apache.iotdb.db.qp.physical.sys.DataAuthPlan;
import org.apache.iotdb.db.qp.physical.sys.DeleteStorageGroupPlan;
import org.apache.iotdb.db.qp.physical.sys.DeleteTimeSeriesPlan;
import org.apache.iotdb.db.qp.physical.sys.FlushPlan;
import org.apache.iotdb.db.qp.physical.sys.LoadConfigurationPlan;
import org.apache.iotdb.db.qp.physical.sys.SetStorageGroupPlan;
import org.apache.iotdb.db.qp.physical.sys.SetTTLPlan;
import org.apache.iotdb.db.qp.physical.sys.ShowTimeSeriesPlan;
import org.apache.iotdb.tsfile.utils.ReadWriteIOUtils;

/**
 * This class is a abstract class for all type of PhysicalPlan.
 */
public abstract class PhysicalPlan {

  private static final String SERIALIZATION_UNIMPLEMENTED = "serialization unimplemented";

  private boolean isQuery;
  private Operator.OperatorType operatorType;
  private static final int NULL_VALUE_LEN = -1;

  //for cluster mode, whether the plan may be splitted into several sub plans
  protected boolean canBeSplit = true;

  //login username, corresponding to cli/session login user info
  private String loginUserName;

  // a bridge from a cluster raft log to a physical plan
  protected long index;

  /**
   * whether the plan can be split into more than one Plans. Only used in the cluster mode.
   */
  public boolean canBeSplit() {
    return canBeSplit;
  }

  protected PhysicalPlan(boolean isQuery) {
    this.isQuery = isQuery;
  }

  protected PhysicalPlan(boolean isQuery, Operator.OperatorType operatorType) {
    this.isQuery = isQuery;
    this.operatorType = operatorType;
  }

  public String printQueryPlan() {
    return "abstract plan";
  }

  public abstract List<PartialPath> getPaths();

  public void setPaths(List<PartialPath> paths) {

  }

  public boolean isQuery() {
    return isQuery;
  }

  public Operator.OperatorType getOperatorType() {
    return operatorType;
  }

  public void setOperatorType(Operator.OperatorType operatorType) {
    this.operatorType = operatorType;
  }

  public List<String> getAggregations() {
    return Collections.emptyList();
  }

  public void setQuery(boolean query) {
    isQuery = query;
  }

  /**
   * Serialize the plan into the given buffer. All necessary fields will be serialized.
   *
   * @param stream
   * @throws IOException
   */
  public void serialize(DataOutputStream stream) throws IOException {
    throw new UnsupportedOperationException(SERIALIZATION_UNIMPLEMENTED);
  }

  /**
   * Serialize the plan into the given buffer. This is provided for WAL, so fields that can be
   * recovered will not be serialized.
   *
   * @param buffer
   */
  public void serialize(ByteBuffer buffer) {
    throw new UnsupportedOperationException(SERIALIZATION_UNIMPLEMENTED);
  }

  /**
   * Deserialize the plan from the given buffer. This is provided for WAL, and must be used with
   * serializeToWAL.
   *
   * @param buffer
   */
  public void deserialize(ByteBuffer buffer) throws IllegalPathException {
    throw new UnsupportedOperationException(SERIALIZATION_UNIMPLEMENTED);
  }

  protected void putString(ByteBuffer buffer, String value) {
    if (value == null) {
      buffer.putInt(NULL_VALUE_LEN);
    } else {
      ReadWriteIOUtils.write(value, buffer);
    }
  }

  protected void putString(DataOutputStream stream, String value) throws IOException {
    if (value == null) {
      stream.writeInt(NULL_VALUE_LEN);
    } else {
      ReadWriteIOUtils.write(value, stream);
    }
  }

  protected String readString(ByteBuffer buffer) {
    int valueLen = buffer.getInt();
    if (valueLen == NULL_VALUE_LEN) {
      return null;
    }
    return ReadWriteIOUtils.readStringWithLength(buffer, valueLen);
  }

  public String getLoginUserName() {
    return loginUserName;
  }

  public void setLoginUserName(String loginUserName) {
    this.loginUserName = loginUserName;
  }

  public static class Factory {

    private Factory() {
      // hidden initializer
    }

    public static PhysicalPlan create(ByteBuffer buffer) throws IOException, IllegalPathException {
      int typeNum = buffer.get();
      if (typeNum >= PhysicalPlanType.values().length) {
        throw new IOException("unrecognized log type " + typeNum);
      }
      PhysicalPlanType type = PhysicalPlanType.values()[typeNum];
      PhysicalPlan plan;
      // TODO-Cluster: support more plans
      switch (type) {
        case INSERT:
          plan = new InsertRowPlan();
          plan.deserialize(buffer);
          break;
        case BATCHINSERT:
          plan = new InsertTabletPlan();
          plan.deserialize(buffer);
          break;
        case DELETE:
          plan = new DeletePlan();
          plan.deserialize(buffer);
          break;
        case SET_STORAGE_GROUP:
          plan = new SetStorageGroupPlan();
          plan.deserialize(buffer);
          break;
        case CREATE_TIMESERIES:
          plan = new CreateTimeSeriesPlan();
          plan.deserialize(buffer);
          break;
        case DELETE_TIMESERIES:
          plan = new DeleteTimeSeriesPlan();
          plan.deserialize(buffer);
          break;
        case TTL:
          plan = new SetTTLPlan();
          plan.deserialize(buffer);
          break;
        case GRANT_WATERMARK_EMBEDDING:
          plan = new DataAuthPlan(OperatorType.GRANT_WATERMARK_EMBEDDING);
          plan.deserialize(buffer);
          break;
        case REVOKE_WATERMARK_EMBEDDING:
          plan = new DataAuthPlan(OperatorType.REVOKE_WATERMARK_EMBEDDING);
          plan.deserialize(buffer);
          break;
        case CREATE_ROLE:
          plan = new AuthorPlan(OperatorType.CREATE_ROLE);
          plan.deserialize(buffer);
          break;
        case DELETE_ROLE:
          plan = new AuthorPlan(OperatorType.DELETE_ROLE);
          plan.deserialize(buffer);
          break;
        case CREATE_USER:
          plan = new AuthorPlan(OperatorType.CREATE_USER);
          plan.deserialize(buffer);
          break;
        case REVOKE_USER_ROLE:
          plan = new AuthorPlan(OperatorType.REVOKE_USER_ROLE);
          plan.deserialize(buffer);
          break;
        case REVOKE_ROLE_PRIVILEGE:
          plan = new AuthorPlan(OperatorType.REVOKE_ROLE_PRIVILEGE);
          plan.deserialize(buffer);
          break;
        case REVOKE_USER_PRIVILEGE:
          plan = new AuthorPlan(OperatorType.REVOKE_USER_PRIVILEGE);
          plan.deserialize(buffer);
          break;
        case GRANT_ROLE_PRIVILEGE:
          plan = new AuthorPlan(OperatorType.GRANT_ROLE_PRIVILEGE);
          plan.deserialize(buffer);
          break;
        case GRANT_USER_PRIVILEGE:
          plan = new AuthorPlan(OperatorType.GRANT_USER_PRIVILEGE);
          plan.deserialize(buffer);
          break;
        case GRANT_USER_ROLE:
          plan = new AuthorPlan(OperatorType.GRANT_USER_ROLE);
          plan.deserialize(buffer);
          break;
        case MODIFY_PASSWORD:
          plan = new AuthorPlan(OperatorType.MODIFY_PASSWORD);
          plan.deserialize(buffer);
          break;
        case DELETE_USER:
          plan = new AuthorPlan(OperatorType.DELETE_USER);
          plan.deserialize(buffer);
          break;
        case DELETE_STORAGE_GROUP:
          plan = new DeleteStorageGroupPlan();
          plan.deserialize(buffer);
          break;
        case SHOW_TIMESERIES:
          plan = new ShowTimeSeriesPlan();
          plan.deserialize(buffer);
          break;
        case LOAD_CONFIGURATION:
          plan = new LoadConfigurationPlan();
          plan.deserialize(buffer);
          break;
        case ALTER_TIMESERIES:
          plan = new AlterTimeSeriesPlan();
          plan.deserialize(buffer);
          break;
        case FLUSH:
          plan = new FlushPlan();
          plan.deserialize(buffer);
          break;
        default:
          throw new IOException("unrecognized log type " + type);
      }
      return plan;
    }
  }

  public enum PhysicalPlanType {
    INSERT, DELETE, BATCHINSERT, SET_STORAGE_GROUP, CREATE_TIMESERIES, TTL, GRANT_WATERMARK_EMBEDDING,
    REVOKE_WATERMARK_EMBEDDING, CREATE_ROLE, DELETE_ROLE, CREATE_USER, REVOKE_USER_ROLE, REVOKE_ROLE_PRIVILEGE,
    REVOKE_USER_PRIVILEGE, GRANT_ROLE_PRIVILEGE, GRANT_USER_PRIVILEGE, GRANT_USER_ROLE, MODIFY_PASSWORD, DELETE_USER,
    DELETE_STORAGE_GROUP, SHOW_TIMESERIES, DELETE_TIMESERIES, LOAD_CONFIGURATION, MULTI_CREATE_TIMESERIES,
    ALTER_TIMESERIES, FLUSH
  }

  public long getIndex() {
    return index;
  }

  public void setIndex(long index) {
    this.index = index;
  }


  /**
   * Check the integrity of the plan in case that the plan is generated by a careless user
   * through Session API.
   * @throws QueryProcessException when the check fails
   */
  public void checkIntegrity() throws QueryProcessException {

  }
}
