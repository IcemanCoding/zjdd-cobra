/**
 * @(#)AppPersonFlowImpl.java 2015年11月28日 Copyright 2015 it.kedacom.com, Inc.
 *                            All rights reserved.
 */

package com.zaijiadd.app.applyflow.service.impl;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zaijiadd.app.applyflow.dao.ApplyContractMapper;
import com.zaijiadd.app.applyflow.dao.ApplyFlowDao;
import com.zaijiadd.app.applyflow.dao.ApplyStoreDao;
import com.zaijiadd.app.applyflow.dao.ApplyStoreDetailDao;
import com.zaijiadd.app.applyflow.dao.ApplyUserRelationDao;
import com.zaijiadd.app.applyflow.dao.BankMapper;
import com.zaijiadd.app.applyflow.dao.CityDealershipMapper;
import com.zaijiadd.app.applyflow.dao.CityMapper;
import com.zaijiadd.app.applyflow.entity.ApplyContract;
import com.zaijiadd.app.applyflow.entity.ApplyStore;
import com.zaijiadd.app.applyflow.entity.ApplyStoreDetail;
import com.zaijiadd.app.applyflow.entity.ApplyUserRelation;
import com.zaijiadd.app.applyflow.entity.City;
import com.zaijiadd.app.applyflow.entity.CityDealership;
import com.zaijiadd.app.applyflow.entity.InviteUserEntity;
import com.zaijiadd.app.applyflow.service.ApplyFlowService;
import com.zaijiadd.app.applyflow.service.AreaService;
import com.zaijiadd.app.system.service.SystemUserService;
import com.zaijiadd.app.user.dao.UserInfoDAO;
import com.zaijiadd.app.user.entity.UserInfoEntity;
import com.zaijiadd.app.utils.constants.ConstantStorePower;
import com.zaijiadd.app.utils.constants.ConstantsRole;

/**
 * (用一句话描述类的主要功能)
 * @author chentao
 * @date 2015年11月28日
 */
@Service
public class ApplyFlowServiceImpl implements ApplyFlowService {

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#addInviteUser(com.zaijiadd.app.applyflow.entity.InviteUserEntity)
	 */
	@Autowired
	ApplyFlowDao applyFlowDao;
	@Autowired
	ApplyStoreDao applyStoreDao;
	@Autowired
	ApplyStoreDetailDao applyStoreDetailDao;

	@Autowired
	BankMapper bankMapper;
	@Autowired
	CityDealershipMapper cityDealershipMapper;

	@Autowired
	AreaService areaService;
	@Autowired
	ApplyUserRelationDao applyUserRelationDao;
	@Autowired
	private UserInfoDAO userInfoDao;
	@Autowired
	private CityMapper cityMapper;
	@Autowired
	private SystemUserService systemUserService;

	@Autowired
	private ApplyContractMapper applyContractMapper;

	@Override
	public Integer addInviteUser(InviteUserEntity inviteUserEntity) {
		return applyFlowDao.addInviteUser(inviteUserEntity);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#updateInviteUser(com.zaijiadd.app.applyflow.entity.InviteUserEntity)
	 */

	@Override
	public Integer updateInviteUser(InviteUserEntity inviteUserEntity) {
		applyFlowDao.updateInviteUser(inviteUserEntity);
		return null;
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#queryInviteUser(java.util.Map)
	 */

	@Override
	public InviteUserEntity queryInviteUser(Map<String, Object> param) {
		return applyFlowDao.queryInviteUser(param);
	}

	/**
	 * @throws Exception
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#addApplyStore(com.zaijiadd.app.applyflow.entity.ApplyStore)
	 */

	@Override
	public String addApplyStore(ApplyStore applyStore) throws Exception {

		Integer applyType = applyStore.getApplyType();// 申请类型
		Integer paymoneyType = applyStore.getPaymoneyType();// 付款类型
		whoCheck(applyStore, applyType, paymoneyType);
		Integer applyStoreId = applyStoreDao.addApplyStore(applyStore);

		String possNum = generateSerialNum();// 生成流水号
		ApplyStore applyStore2 = new ApplyStore();
		applyStore2.setPossNum(possNum);
		applyStore2.setApplyStoreId(applyStore.getApplyStoreId());
		updateApplyStore(applyStore2);
		return possNum;
	}

	/**
	 * 谁审核
	 * @param applyStore
	 * @param applyType
	 * @param paymoneyType
	 * @throws Exception
	 */

	void whoCheck(ApplyStore applyStore, Integer applyType, Integer paymoneyType) throws Exception {

		if (ConstantStorePower.APPLY_PAYMONEY_NOTALL.equals(paymoneyType)) {// 定金直接给财务
			applyStore.setWhoCheck(ConstantsRole.ROLE_FINANCE);
		} else {// 全额
			if (applyType.equals(ConstantStorePower.APPLY_TYPE_DEALERSHIP)) {// 经销权

				BigDecimal personPaymoneyCount = getPersonPaymoneyCount(applyStore);
				// 经销权价格计算
				BigDecimal needPaymoneyCount = getDealershipMoney(applyStore);

				if (personPaymoneyCount.compareTo(needPaymoneyCount) < 0) {// 没有支付全额
					UserInfoEntity leader = systemUserService.getLeader(applyStore.getYjsUserId());
					// 实际付的金额比应收的金额小，那么给主管审批
					applyStore.setWhoCheck(leader.getUserId());
					applyStore.setWhetherStartApply(ConstantStorePower.WHETHER_STARTAPPLY_YES);//
					// 没有发起收款申请
				} else {// 实际付的金额比应收的金额 相等，给财务
					applyStore.setWhoCheck(ConstantsRole.ROLE_FINANCE);
				}
			} else if (applyType.equals(ConstantStorePower.APPLY_TYPE_SMALLSTORE)) {// 小店

				BigDecimal personPaymoneyCount = getPersonPaymoneyCount(applyStore);
				// 小店价格计算
				BigDecimal needPaymoneyCount = getNeedPaymoneyCount(applyStore);

				if (personPaymoneyCount.compareTo(needPaymoneyCount) < 0) {// 没有支付全额
					UserInfoEntity leader = systemUserService.getLeader(applyStore.getYjsUserId());
					// 实际付的金额比应收的金额小，那么给主管审批
					applyStore.setWhoCheck(leader.getUserId());
					applyStore.setWhetherStartApply(ConstantStorePower.WHETHER_STARTAPPLY_YES);//
					// 没有发起收款申请
				} else {// 实际付的金额比应收的金额 相等，给财务
					applyStore.setWhoCheck(ConstantsRole.ROLE_FINANCE);
				}
			}
		}
	}

	/**
	 * (用一句话描述方法的主要功能)
	 * @param applyStore
	 * @return
	 * @throws Exception
	 */

	BigDecimal getDealershipMoney(ApplyStore applyStore) throws Exception {
		Integer dealershipNum = applyStore.getDealershipNum();// 经销权个数
		BigDecimal dealershipNumBig = new BigDecimal(dealershipNum);
		BigDecimal cityDealershipMoney = new BigDecimal(1);
		// Map<String, Object> findCitySellInfo =
		// areaService.findCitySellInfo(applyStore.getCityId(),
		// applyStore.getDistrictId());
		CityDealership cityDealership = cityDealershipMapper.getCityMoneyByCityId(applyStore.getCityId());
		if (cityDealership != null) {
			cityDealershipMoney = cityDealership.getCityDealershipMoney();
			// cityDealershipMoney = (BigDecimal) findCitySellInfo.get("money");
			// BigDecimal dealershipNumAble = (BigDecimal)
			// findCitySellInfo.get("laveNum");
		}
		BigDecimal needPaymoneyCount = new BigDecimal(0);
		if (dealershipNumBig.compareTo(BigDecimal.ZERO) == 0) {
			needPaymoneyCount = cityDealershipMoney;
		} else {
			needPaymoneyCount = cityDealershipMoney.multiply(dealershipNumBig);// 每个城市的价格X个数，需支付的
		}
		return needPaymoneyCount;
	}

	/**
	 * (用一句话描述方法的主要功能)
	 * @param applyStore
	 * @return
	 */

	BigDecimal getNeedPaymoneyCount(ApplyStore applyStore) {
		return ConstantStorePower.STORE_MONEY;
	}

	/**
	 * (用一句话描述方法的主要功能)
	 * @param applyStore
	 * @return
	 */

	BigDecimal getPersonPaymoneyCount(ApplyStore applyStore) {
		Integer cityId = applyStore.getCityId();
		BigDecimal paidMoney = applyStore.getPaidMoney();// 已付金额
		BigDecimal needPaymoney = applyStore.getNeedPaymoney();// 应付金额
		BigDecimal personPaymoneyCount = paidMoney.add(needPaymoney);// 个人现在已经付的金额
		return personPaymoneyCount;
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#queryAllApplyStore(java.util.Map)
	 */

	@Override
	public List<Map<String, Object>> queryAllApplyStore(Map<String, Object> param) {
		return applyFlowDao.queryAllApplyStore(param);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#queryInviteUserLike(java.util.Map)
	 */

	@Override
	public List<Map<String, Object>> queryInviteUserLike(Map<String, Object> param) {
		return applyFlowDao.queryInviteUserLike(param);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#queryInviteUserMap(java.util.Map)
	 */

	@Override
	public List<Map<String, Object>> queryInviteUserMap(Map<String, Object> param) {
		return applyFlowDao.queryInviteUserMap(param);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#updateApplyStore(com.zaijiadd.app.applyflow.entity.ApplyStore)
	 */

	@Override
	public Integer updateApplyStore(ApplyStore applyStore) {
		return applyStoreDao.updateApplyStore(applyStore);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#queryApplyStoreDetails(Integer)
	 */

	@Override
	public Map<String, Object> queryApplyStoreDetails(Integer applyStoreId) {
		return applyStoreDao.queryApplyStoreDetails(applyStoreId);
	}

	/**
	 * @return
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#getCityDealershipMoney(Integer)
	 */

	@Override
	public BigDecimal getCityDealershipMoney(Integer cityId) {
		ArrayList<City> city = cityMapper.selectCityByID(cityId);
		if (city.size() > 0) {
			return city.get(0).getCityMoney();

		}
		return new BigDecimal(1);

	}

	/**
	 * (用一句话描述方法的主要功能)
	 * @param applyUserRelation
	 */

	private void insertApplyRoleRelation(ApplyUserRelation applyUserRelation) {
		applyUserRelationDao.insertApplyRoleRelation(applyUserRelation);

	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#queryAllApplyStoreSate(java.util.Map)
	 */

	@Override
	public List<Map<String, Object>> queryAllApplyStoreSate(Map<String, Object> param) {
		return applyStoreDao.queryAllApplyStoreSate(param);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#queryFinanceApproveStoreTry(java.util.Map)
	 */

	@Override
	public List<Map<String, Object>> queryFinanceApproveStoreTry(Map<String, Object> param) {
		return applyStoreDao.queryFinanceApproveStoreTry(param);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#queryManagersApproveStoreTry(java.util.Map)
	 */

	@Override
	public List<Map<String, Object>> queryManagersApproveStoreTry(Map<String, Object> param) {
		return applyStoreDao.queryManagersApproveStoreTry(param);
	}

	/**
	 * @throws Exception
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#roleApproveStore(java.util.Map)
	 */

	@Override
	public Integer roleApproveStore(Map<String, Object> param) throws Exception {
		Integer roleId = (Integer) param.get("roleId");
		Integer userId = (Integer) param.get("userId");
		Integer applyStoreId = (Integer) param.get("applyStoreId");
		Integer approveState = (Integer) param.get("approveState");

		ApplyUserRelation applyUserRelation = new ApplyUserRelation();// 操作记录
		applyUserRelation.setApplyId(applyStoreId);
		applyUserRelation.setUserid(userId);// userId
		applyUserRelation.setRoleid(roleId);

		if (ConstantsRole.ROLE_FINANCE.equals(roleId)) {// 财务
			if (approveState == ConstantStorePower.approve_state_succ) {// 财务同意
				financeApproveApply(applyStoreId, approveState, applyUserRelation);
			}

		} else if (ConstantsRole.ROLE_MANAGERS.equals(roleId)) {// 主管
			applyUserRelation.setCaurApproveState(approveState);
			if (approveState == ConstantStorePower.approve_state_succ) {// 主管同意
				managersApproveApply(applyStoreId, approveState, applyUserRelation);
			} else if (approveState == ConstantStorePower.approve_state_fail) {// 主管拒绝
				managersNotApproveApply(applyStoreId, approveState, applyUserRelation);

			}
		}
		return null;
	}

	/**
	 * 主管拒绝
	 * @param applyStoreId
	 * @param approveState
	 * @param applyUserRelation
	 */

	void managersNotApproveApply(Integer applyStoreId, Integer approveState, ApplyUserRelation applyUserRelation) {
		applyUserRelation.setApplyId(applyStoreId);
		applyUserRelation.setCaurApproveState(approveState);
		this.insertApplyRoleRelation(applyUserRelation);

		ApplyStore applyStore = new ApplyStore();
		applyStore.setApplyStoreId(applyStoreId);
		applyStore.setApplyStatus(ConstantStorePower.approve_state_fail);// 单子的状态
		applyStore.setManagersCheck(ConstantStorePower.approve_state_fail);// 单子的经理审核状态
		this.updateApplyStore(applyStore);
	}

	/**
	 * 主管同意
	 * @param applyStoreId
	 * @param approveState
	 * @param applyUserRelation
	 */

	void managersApproveApply(Integer applyStoreId, Integer approveState, ApplyUserRelation applyUserRelation) {
		applyUserRelation.setApplyId(applyStoreId);
		applyUserRelation.setCaurApproveState(approveState);
		this.insertApplyRoleRelation(applyUserRelation);

		ApplyStore applyStore = new ApplyStore();
		applyStore.setApplyStoreId(applyStoreId);
		applyStore.setWhoCheck(ConstantsRole.ROLE_FINANCE);
		applyStore.setApplyStatus(ConstantStorePower.apply_state_ready);// 单子的状态
		applyStore.setManagersCheck(ConstantStorePower.approve_state_succ);// 单子的经理审核状态
		applyStore.setWhetherStartApply(ConstantStorePower.WHETHER_STARTAPPLY_NO);// 重置
		this.updateApplyStore(applyStore);
	}

	/**
	 * 财务同意
	 * @param applyStoreId
	 * @param approveState
	 * @param applyUserRelation
	 * @throws Exception
	 */

	void financeApproveApply(Integer applyStoreId, Integer approveState, ApplyUserRelation applyUserRelation)
			throws Exception {
		applyUserRelation.setApplyId(applyStoreId);
		applyUserRelation.setCaurApproveState(approveState);
		this.insertApplyRoleRelation(applyUserRelation);

		// updateDealershipNum(applyStoreId);// 更新城市的经销权个数

		Map<String, Object> queryApplyStoreDetails = this.queryApplyStoreDetails(applyStoreId);
		Integer paymoneyType = (Integer) queryApplyStoreDetails.get("paymoneyType");
		ApplyStore applyStore = new ApplyStore();
		if (ConstantStorePower.APPLY_PAYMONEY_NOTALL.equals(paymoneyType)) {// 定金
			applyStore.setApplyStatus(ConstantStorePower.apply_state_fail);// 单子的状态--in
		}
		applyStore.setApplyStoreId(applyStoreId);// id
		applyStore.setApplyStatus(ConstantStorePower.apply_state_succ);// 单子的状态
		applyStore.setFinanceCheck(ConstantStorePower.apply_state_succ);// 单子的财务状态
		this.updateApplyStore(applyStore);
		if (ConstantStorePower.APPLY_PAYMONEY_ALL.equals(paymoneyType)) {// 全额
			ApplyStore applyStoreCopy = this.applyStoreDao.selectByAppStoreId(applyStoreId);
			Integer storeNumm = applyStoreCopy.getStoreNumm();
			if(storeNumm != null) {
				for (int i = 0; i < storeNumm; i++) {
					ApplyStoreDetail applyDetailStore = new ApplyStoreDetail();
					try {
						PropertyUtils.copyProperties(applyDetailStore, applyStoreCopy);
						applyDetailStore.setApplyStatus(1);
						applyStoreDetailDao.addApplyStore(applyDetailStore);
					} catch (IllegalAccessException e) {
						e.printStackTrace();
					} catch (InvocationTargetException e) {
						e.printStackTrace();
					} catch (NoSuchMethodException e) {
						e.printStackTrace();
					}
				}
			}

		}

	}

	/**
	 * (用一句话描述方法的主要功能)
	 * @param applyStoreId
	 * @return
	 */

	private ApplyStore selectByAppStoreId(Integer applyStoreId) {
		return applyStoreDao.selectByAppStoreId(applyStoreId);
	}

	/**
	 * 更新城市的经销权个数
	 * @param applyStoreId
	 * @throws Exception
	 */

	void updateDealershipNum(Integer applyStoreId) throws Exception {
		Map<String, Object> queryApplyStoreDetails = applyStoreDao.queryApplyStoreDetails(applyStoreId);
		Integer cityId = (Integer) queryApplyStoreDetails.get("cityId");// 用户的城市id
		Integer districtId = (Integer) queryApplyStoreDetails.get("districtId");// 用户的城市id
		Integer dealershipNum = (Integer) queryApplyStoreDetails.get("dealershipNum");// 用户的经销权个数
		if (dealershipNum != null && dealershipNum != 0) {
			CityDealership cityDealership = cityDealershipMapper.getCityMoneyByCityId(cityId);
			Map<String, Object> findCitySellInfo = areaService.findCitySellInfo(cityId, districtId);
			if (cityDealership != null) {
				Integer dealershipNumAble = (Integer) findCitySellInfo.get("laveNum");
				dealershipNumAble = dealershipNumAble - dealershipNum;
				if (dealershipNumAble >= 0) {
					CityDealership cityDealership2 = new CityDealership(cityDealership.getCityDealershipId(), cityId,
							dealershipNumAble);
					cityDealershipMapper.updateCityDealership(cityDealership2);
				} else {
					throw new RuntimeException("超出了城市的经销权总个数");
				}

			}
		}

	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#queryApproveMsg(java.util.Map)
	 */

	@Override
	public List<Map<String, Object>> queryApproveMsg(Map<String, Object> param) {
		return applyUserRelationDao.queryApproveMsg(param);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#printContract(Map)
	 */

	@Override
	public Map<String, Object> printContract(Map<String, Object> param) {
		return applyStoreDao.printContract(param);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#queryInviteUserDet(java.util.Map)
	 */

	@Override
	public Map<String, Object> queryInviteUserDet(Map<String, Object> param) {
		return applyFlowDao.queryInviteUserDet(param);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#queryInviteUserMsgDet(java.util.Map)
	 */

	@Override
	public Map<String, Object> queryInviteUserMsgDet(Map<String, Object> param) {
		return applyFlowDao.queryInviteUserMsgDet(param);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#updateInviteUserById(java.util.Map)
	 */

	@Override
	public Integer updateInviteUserById(Map<String, Object> param) {
		return applyFlowDao.updateInviteUserById(param);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#queryRoleApproveStoreTry(java.util.Map)
	 */

	@Override
	public List<Map<String, Object>> queryRoleApproveStoreTry(Map<String, Object> param) {
		return applyStoreDao.queryRoleApproveStoreTry(param);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#queryBankList(java.util.Map)
	 */

	@Override
	public List<Map<String, Object>> queryBankList(Map<String, Object> param) {
		return bankMapper.queryBankList(param);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#updateWhetherStartApply(com.zaijiadd.app.applyflow.entity.ApplyStore)
	 */

	@Override
	public Integer updateWhetherStartApply(ApplyStore applyStore) {
		return applyStoreDao.updateWhetherStartApply(applyStore);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#queryDealershipNumAble(java.lang.Integer)
	 */

	@Override
	public Map<String, Object> queryDealershipNumAble(Integer cityId) {
		return cityDealershipMapper.queryDealershipNumAble(cityId);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#updateUserAddFlagById(java.util.Map)
	 */

	@Override
	public Integer updateUserAddFlagById(Map<String, Object> param) {
		return applyFlowDao.updateUserAddFlagById(param);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#queryAllInviteUserMsg(java.util.Map)
	 */

	@Override
	public List<Map<String, Object>> queryAllInviteUserMsg(Map<String, Object> param) {
		return applyFlowDao.queryAllInviteUserMsg(param);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#queryInviteUserMsgLike(java.util.Map)
	 */

	@Override
	public List<Map<String, Object>> queryInviteUserMsgLike(Map<String, Object> param) {
		return applyFlowDao.queryInviteUserMsgLike(param);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#generateSerialNum()
	 */

	@Override
	public String generateSerialNum() {
		Random r = new Random();
		Double d = r.nextDouble();
		String s = d + "";
		return s = s.substring(3, 3 + 6);
	}

	/**
	 * @throws Exception
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#payRemainMoney(com.zaijiadd.app.applyflow.entity.ApplyStore)
	 */

	@Override
	public String payRemainMoney(ApplyStore applyStore) throws Exception {
		BigDecimal contractAmount = applyStore.getContractAmount();
		BigDecimal needPaymoney = applyStore.getNeedPaymoney();
		BigDecimal paidMoney = applyStore.getPaidMoney();
		Integer payWay = applyStore.getPayWay();
		Integer paymoneyType2 = applyStore.getPaymoneyType();
		Integer whetherSelf = applyStore.getWhetherSelf();

		ApplyStore applyStore2 = this.selectByAppStoreId(applyStore.getApplyStoreId());// 老的记录

		ApplyStore applyStore3 = new ApplyStore();

		BeanUtils.copyProperties(applyStore3, applyStore2);
		applyStore3.setFinanceCheck(ConstantStorePower.approve_state_ready);
		applyStore3.setManagersCheck(ConstantStorePower.approve_state_ready);
		applyStore3.setApplyStatus(ConstantStorePower.apply_state_ready);
		applyStore3.setWhetherStartApply(ConstantStorePower.WHETHER_STARTAPPLY_NO);
		applyStore3.setContractAmount(contractAmount);
		applyStore3.setNeedPaymoney(needPaymoney);
		applyStore3.setPaidMoney(paidMoney);
		applyStore3.setPayWay(payWay);
		applyStore3.setPaymoneyType(paymoneyType2);
		applyStore3.setWhetherSelf(whetherSelf);
		applyStore3.setWhoCheck(null);
		//
		Integer applyStoreId = applyStoreDao.addApplyStore(applyStore3);//
		Integer applyStoreId2 = applyStore3.getApplyStoreId();
		applyStore3.setApplyStoreId(applyStoreId2);
		// 新记录
		Integer applyType = applyStore3.getApplyType();
		Integer paymoneyType = applyStore3.getPaymoneyType();// 付款类型
		whoCheckpayRemainMoney(applyStore3, applyType, paymoneyType);
		updateApplyStore(applyStore3);
		// 把原来的记录备份
		applyStore2.setApplyStatus(ConstantStorePower.APPLY_STATE_NOT_PAYALLMONEY);// 尾款，用户不可见
		updateApplyStore(applyStore2);
		String possNum = generateSerialNum();// 生成流水号
		return possNum;
	}

	/**
	 * (用一句话描述方法的主要功能)
	 * @param applyStore
	 * @param applyType
	 * @param paymoneyType
	 * @throws Exception
	 */

	private void whoCheckpayRemainMoney(ApplyStore applyStore, Integer applyType, Integer paymoneyType)
			throws Exception {
		Map<String, Object> applyStoreMap = queryApplyStoreDetails(applyStore.getApplyStoreId());
		applyType = (Integer) applyStoreMap.get("applyType");
		Integer cityId = (Integer) applyStoreMap.get("cityId");
		Integer dealershipNum = (Integer) applyStoreMap.get("dealershipNum");
		Integer storeNumm = (Integer) applyStoreMap.get("storeNumm");
		Integer userId = (Integer) applyStoreMap.get("userId");

		applyStore.setCityId(cityId);
		applyStore.setDealershipNum(dealershipNum);
		applyStore.setStoreNumm(storeNumm);
		applyStore.setYjsUserId(userId);
		if (ConstantStorePower.APPLY_PAYMONEY_NOTALL.equals(paymoneyType)) {// 定金直接给财务
			applyStore.setWhoCheck(ConstantsRole.ROLE_FINANCE);
		} else {// 全额
			if (applyType.equals(ConstantStorePower.APPLY_TYPE_DEALERSHIP)) {// 经销权

				BigDecimal personPaymoneyCount = getPersonPaymoneyCount(applyStore);
				// 经销权价格计算
				BigDecimal needPaymoneyCount = getDealershipMoney(applyStore);

				if (personPaymoneyCount.compareTo(needPaymoneyCount) < 0) {// 没有支付全额
					UserInfoEntity leader = systemUserService.getLeader(applyStore.getYjsUserId());
					// 实际付的金额比应收的金额小，那么给主管审批
					applyStore.setWhoCheck(leader.getUserId());
					applyStore.setWhetherStartApply(ConstantStorePower.WHETHER_STARTAPPLY_YES);//
				} else {// 实际付的金额比应收的金额 相等，给财务
					applyStore.setWhoCheck(ConstantsRole.ROLE_FINANCE);
				}
			} else if (applyType.equals(ConstantStorePower.APPLY_TYPE_SMALLSTORE)) {// 小店

				BigDecimal personPaymoneyCount = getPersonPaymoneyCount(applyStore);
				// 小店价格计算
				BigDecimal needPaymoneyCount = getNeedPaymoneyCount(applyStore);

				if (personPaymoneyCount.compareTo(needPaymoneyCount) < 0) {// 没有支付全额
					UserInfoEntity leader = systemUserService.getLeader(applyStore.getYjsUserId());
					// 实际付的金额比应收的金额小，那么给主管审批
					applyStore.setWhoCheck(leader.getUserId());
					applyStore.setWhetherStartApply(ConstantStorePower.WHETHER_STARTAPPLY_YES);//
				} else {// 实际付的金额比应收的金额 相等，给财务
					applyStore.setWhoCheck(ConstantsRole.ROLE_FINANCE);
				}
			}
		}
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#whetherPayAllMoney(com.zaijiadd.app.applyflow.entity.ApplyStore)
	 */

	@Override
	public Boolean whetherPayAllMoney(ApplyStore applyStore) throws Exception {
		Integer applyType = applyStore.getApplyType();// 申请类型
		if (applyType.equals(ConstantStorePower.APPLY_TYPE_DEALERSHIP)) {// 经销权
			BigDecimal personPaymoneyCount = getPersonPaymoneyCount(applyStore);
			// 经销权价格计算
			BigDecimal needPaymoneyCount = getDealershipMoney(applyStore);

			if (personPaymoneyCount.compareTo(needPaymoneyCount) < 0) {// 没有支付全额
				return false;
			}
		} else if (applyType.equals(ConstantStorePower.APPLY_TYPE_SMALLSTORE)) {// 小店
			BigDecimal personPaymoneyCount = getPersonPaymoneyCount(applyStore);
			// 小店价格计算
			BigDecimal needPaymoneyCount = getNeedPaymoneyCount(applyStore);

			if (personPaymoneyCount.compareTo(needPaymoneyCount) < 0) {// 没有支付全额
				return false;
			}
		}

		return true;
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#cleanLoseEfficacyApplyStore()
	 */

	@Override
	public void cleanLoseEfficacyApplyStore() {
		Map<String, Object> param = new HashMap<String, Object>();
		List<ApplyStore> applyStoreList = applyStoreDao.queryApplStoreNotAllMoney(param);
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		for (ApplyStore applyStore : applyStoreList) {
			Date createdDate = applyStore.getCreatedDate();

		}

	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#getApplyContract(java.util.Map)
	 */

	@Override
	public ApplyContract getApplyContract(Map<String, Object> applyContractParam) {
		return applyContractMapper.getApplyContract(applyContractParam);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#getUserInfoById(java.lang.Integer)
	 */

	@Override
	public UserInfoEntity getUserInfoById(Integer userId) {
		return userInfoDao.getUserInfoById(userId);
	}

	/**
	 * @see com.zaijiadd.app.applyflow.service.ApplyFlowService#queryAllApplyStoreSateIn(java.util.Map)
	 */

	@Override
	public List<Map<String, Object>> queryAllApplyStoreSateIn(Map<String, Object> param) {
		return applyStoreDao.queryAllApplyStoreSateIn(param);
	}

}
