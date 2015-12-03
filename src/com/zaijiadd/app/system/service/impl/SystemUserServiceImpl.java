package com.zaijiadd.app.system.service.impl;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zaijiadd.app.system.service.SystemUserService;
import com.zaijiadd.app.user.dao.ShopUserInfoDAO;
import com.zaijiadd.app.user.dao.UserInfoDAO;
import com.zaijiadd.app.user.entity.UserInfoEntity;

@Service
public class SystemUserServiceImpl implements SystemUserService {
	
	@Autowired
	private UserInfoDAO userInfoDAO;
	@Autowired
	private ShopUserInfoDAO shopUserInfoDAO;
	

	@Override
	public UserInfoEntity getUserInfoForLogin(String username, String password) throws Exception {
		Map<String, String> map = new HashMap<>();
		map.put("username", username);
		map.put("password", password);
		return userInfoDAO.getUserInfoForLogin(map);
	}

	@Override
	public UserInfoEntity registerUser(String mobile) throws Exception {
		UserInfoEntity userInfoEntity = new UserInfoEntity();
		userInfoEntity.setMobile(mobile);
		userInfoEntity.setPassword("e10adc3949ba59abbe56e057f20f883e");
		shopUserInfoDAO.insert(userInfoEntity);
		return userInfoEntity;
	}

}