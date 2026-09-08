package com.example.practice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.practice.common.BusinessException;
import com.example.practice.dto.LoginDTO;
import com.example.practice.dto.PhoneLoginDTO;
import com.example.practice.dto.RegisterDTO;
import com.example.practice.entity.User;
import com.example.practice.mapper.UserMapper;
import com.example.practice.security.LoginUser;
import com.example.practice.service.AuthService;
import com.example.practice.util.JwtUtils;
import com.example.practice.vo.LoginVO;
import com.example.practice.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现类：注册、登录、手机号验证码
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 验证码在 Redis 中的 key 前缀 */
    private static final String CODE_PREFIX = "sms:code:";

    /** 防重复发送的 key 前缀 */
    private static final String INTERVAL_PREFIX = "sms:interval:";

    /** 验证码有效期：5 分钟 */
    private static final long CODE_TTL_MINUTES = 5;

    /** 两次发送最小间隔：60 秒 */
    private static final long SEND_INTERVAL_SECONDS = 60;

    @Override
    public void register(RegisterDTO dto) {
        // 基础校验
        if (!StringUtils.hasText(dto.getUsername()) || !StringUtils.hasText(dto.getPassword())) {
            throw new BusinessException("用户名和密码不能为空");
        }
        if (!StringUtils.hasText(dto.getPhone())) {
            throw new BusinessException("手机号不能为空");
        }

        // 校验用户名唯一
        Long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (usernameCount > 0) {
            throw new BusinessException("用户名已存在");
        }

        // 校验手机号唯一
        Long phoneCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));
        if (phoneCount > 0) {
            throw new BusinessException("该手机号已被注册");
        }

        // 校验手机号验证码（与验证码登录复用同一个校验逻辑）
        verifyCode(dto.getPhone(), dto.getCode());

        // 组装用户并加密密码
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setPhone(dto.getPhone());
        // 昵称默认取手机号后 4 位，方便区分
        user.setNickname(StringUtils.hasText(dto.getNickname())
                ? dto.getNickname()
                : "用户" + dto.getPhone().substring(dto.getPhone().length() - 4));
        user.setGender(0);
        user.setStatus(0);
        userMapper.insert(user);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        // 1. 构建认证请求（用户名 + 明文密码）
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword());

        // 2. 交给 AuthenticationManager 校验：
        //    内部会调用 UserDetailsService 查用户 + PasswordEncoder 比对 BCrypt 密码
        //    校验失败会抛 BadCredentialsException（被全局异常处理器转成"用户名或密码错误"）
        Authentication authentication = authenticationManager.authenticate(authenticationToken);
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();

        // 3. 校验通过，签发 token 并返回用户信息
        return buildLoginVO(loginUser);
    }

    @Override
    public LoginVO loginByPhone(PhoneLoginDTO dto) {
        if (!StringUtils.hasText(dto.getPhone())) {
            throw new BusinessException("手机号不能为空");
        }

        // 1. 校验验证码（错误或过期直接抛异常）
        verifyCode(dto.getPhone(), dto.getCode());

        // 2. 按手机号查用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));

        // 3. 用户不存在则自动注册（手机号验证码登录的常见做法）
        if (user == null) {
            user = new User();
            user.setPhone(dto.getPhone());
            user.setNickname("用户" + dto.getPhone().substring(dto.getPhone().length() - 4));
            user.setGender(0);
            user.setStatus(0);
            userMapper.insert(user);
        }

        // 4. 签发 token
        return buildLoginVO(new LoginUser(user));
    }

    @Override
    public void sendCode(String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new BusinessException("手机号不能为空");
        }

        // 1. 防重复发送：60 秒内不能再次发送
        String intervalKey = INTERVAL_PREFIX + phone;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(intervalKey))) {
            throw new BusinessException("发送过于频繁，请 60 秒后再试");
        }

        // 2. 生成 6 位随机验证码
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));

        // 3. 验证码存入 Redis，5 分钟有效
        stringRedisTemplate.opsForValue().set(CODE_PREFIX + phone, code, CODE_TTL_MINUTES, TimeUnit.MINUTES);

        // 4. 记录发送间隔，60 秒内禁止再次发送
        stringRedisTemplate.opsForValue().set(intervalKey, "1", SEND_INTERVAL_SECONDS, TimeUnit.SECONDS);

        // 5. 练习环境：仅打印到控制台（生产环境这里接入短信平台真实发送）
        log.info("【验证码】手机号 {} 的验证码为：{}（5 分钟内有效）", phone, code);
    }

    /**
     * 校验手机号验证码：从 Redis 取出比对，成功后删除（一次性使用）
     */
    private void verifyCode(String phone, String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("验证码不能为空");
        }
        String cachedCode = stringRedisTemplate.opsForValue().get(CODE_PREFIX + phone);
        if (cachedCode == null || !cachedCode.equals(code)) {
            throw new BusinessException("验证码错误或已过期");
        }
        // 验证码一次性使用：校验通过后立即删除
        stringRedisTemplate.delete(CODE_PREFIX + phone);
    }

    /**
     * 构造登录成功返回体：签发 token + 用户信息
     */
    private LoginVO buildLoginVO(LoginUser loginUser) {
        String token = jwtUtils.createToken(loginUser.getId(), loginUser.getUsername());
        return LoginVO.of(token, UserVO.from(loginUser));
    }
}
