package com.atguigu.gmall.cart.service.impl;

import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartAsyncService;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.util.DateUtil;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private CartAsyncService cartAsyncService;

    @Autowired
    private CartInfoMapper cartInfoMapper;

    @Override
    public void addToCart(Long skuId, String userId, Integer skuNum) {
        /*
        1.添加购物车前，先判断购物车中是否有该商品
        true：
            商品数量增加
        false：
            加入购物车
        2.将数据同步到redis
         */

        //  数据类型hash + key hset(key,field,value)
        //  key = user:userId:cart ,谁的购物车 field = skuId value = cartInfo
        String cartKey = this.getCartKey(userId);
        //  判断缓存中是否有购物车的key
        if(!redisTemplate.hasKey(cartKey)){
            //没数据  加载 数据库 并放入缓存
            this.loadCartCache(userId);
        }
        //查看缓存 hget(key,field)
        CartInfo cartInfoExist = (CartInfo) redisTemplate.opsForHash().get(cartKey,skuId.toString());
        if (cartInfoExist != null) {
            //当前购物车有商品
            //数量增加
            cartInfoExist.setSkuNum(cartInfoExist.getSkuNum() + skuNum);
            //初始化实时价格   本质skuPrice = skuInfo.price
            cartInfoExist.setSkuPrice(productFeignClient.getSkuPrice(skuId));
            //修改更新时间
            cartInfoExist.setUpdateTime(new Timestamp(new Date().getTime()));
            //再次添加商品时，默认选中状态
            cartInfoExist.setIsChecked(1);
            //修改数据库
            cartAsyncService.updateCartInfo(cartInfoExist);
        }else {
            //第一次添加购物车
            CartInfo cartInfo = new CartInfo();
            SkuInfo skuInfo = productFeignClient.getAttrValueList(skuId);
            cartInfo.setUserId(userId);
            cartInfo.setSkuId(skuId);
            //在初始化的时候，添加放入购物车时的价格
            cartInfo.setCartPrice(skuInfo.getPrice());
            //  数据库不存在的，购物车的价格 = skuInfo.price
            cartInfo.setSkuPrice(skuInfo.getPrice());
            cartInfo.setSkuNum(skuNum);
            cartInfo.setImgUrl(skuInfo.getSkuDefaultImg());
            cartInfo.setSkuName(skuInfo.getSkuName());
            cartInfo.setUpdateTime(new Timestamp(new Date().getTime()));
            cartInfo.setCreateTime(new Timestamp(new Date().getTime()));
            //保存到数据库
            cartAsyncService.saveCartInfo(cartInfo);
            cartInfoExist = cartInfo;
        }

        //将数据放入缓存  hset(key,field,value);
        redisTemplate.opsForHash().put(cartKey,skuId.toString(),cartInfoExist);
        //设置过期时间
        this.setCartKeyExpire(cartKey);
    }

    @Override
    public List<CartInfo> getCartList(String userId, String userTempId) {
        //声明一个返回的集合对象
        List<CartInfo> cartInfoList = new ArrayList<>();
        if (StringUtils.isEmpty(userId)) {
            //未登录，获取未登陆的购物车数据
            cartInfoList = this.getCartList(userTempId);
        }
        /*
            1.准备合并购物车
            2.获取临时（未登录）的购物车数据
            3.如果临时购物车中有数据，则进行合并  合并规则：skuId相同，则数量相加，合并完成之后，删除临时数据
            4.如果未登陆的购物车中没有数据，则直接显示已登陆的数据
         */
        if (!StringUtils.isEmpty(userId)) {
            //已登录
            //获取临时数据（未登录时添加的购物车数据）
            List<CartInfo> cartTempList = this.getCartList(userTempId);
            if (!CollectionUtils.isEmpty(cartTempList)) {
                //有临时数据（未登录时添加的购物车数据），根据skuId进行合并
                cartInfoList = this.mergeToCartList(cartTempList,userId);
                //删除临时数据（未登录时添加的购物车数据）
                this.deleteCartList(userTempId);
            }
            //如果未登录购物车中没有数据
            if(StringUtils.isEmpty(userTempId) || CollectionUtils.isEmpty(cartTempList)){
                //直接显示已登陆的数据
                cartInfoList = this.getCartList(userId);
            }
        }
        return cartInfoList;
    }

    /**
     * 有临时数据，删除临时数据的方法
     * @param userTempId
     */
    private void deleteCartList(String userTempId) {
        //删除数据库
//        cartInfoMapper.delete(new QueryWrapper<CartInfo>().eq("user_id",userTempId));
        cartAsyncService.deleteCartInfo(userTempId); //异步删除
        //删除缓存
        String cartKey = getCartKey(userTempId);
        Boolean flag = redisTemplate.hasKey(cartKey);
        if (flag) {
            redisTemplate.delete(cartKey);
        }
    }

    /**
     * 有临时数据，合并购物车
     * @param cartInfoNoLoginList
     * @param userId
     * @return
     */
    private List<CartInfo> mergeToCartList(List<CartInfo> cartInfoNoLoginList, String userId) {
        //根据用户id查询登陆的数据
        List<CartInfo> cartInfoLoginList = this.getCartList(userId);
        //  登录购物车数据要是空，则直接返回{需要将未登录的数据添加到数据库}
        if(CollectionUtils.isEmpty(cartInfoLoginList)) return cartInfoNoLoginList;

        //  登录的购物车数据不为空！ 合并条件skuId    map<skuId,cartInfo>
        Map<Long, CartInfo> longCartInfoMap  = cartInfoLoginList
                .stream()
                .collect(Collectors.toMap(CartInfo::getSkuId, cartInfo -> cartInfo));

        //遍历集合，判断未登陆的skuId在已登录的map 中是否有这个key
        for (CartInfo cartInfo : cartInfoNoLoginList) {
            Long skuId = cartInfo.getSkuId();
            if(longCartInfoMap.containsKey(skuId)){
                //存在相同skuId的商品，数量相加
                //用当前skuId获取对应的 已登录的 cartInfo对象
                CartInfo cartInfoLogin = longCartInfoMap.get(skuId);
                //赋值商品数量
                cartInfoLogin.setSkuNum(cartInfoLogin.getSkuNum() + cartInfo.getSkuNum());
                //更新 updateTime
                cartInfoLogin.setUpdateTime(new Timestamp(new Date().getTime()));
                //合并购物车时的选择状态，以未登录选中状态为基准
                if (cartInfo.getIsChecked() == 1) {
                    cartInfoLogin.setIsChecked(1);
                }
                //更新数据库，同步更新
                QueryWrapper<CartInfo> cartInfoQueryWrapper = new QueryWrapper<>();
                cartInfoQueryWrapper.eq("user_id",cartInfoLogin.getUserId());
                cartInfoQueryWrapper.eq("sku_id",cartInfoLogin.getSkuId());
                cartInfoMapper.update(cartInfoLogin,cartInfoQueryWrapper);
                //  异步更新 代码不会走这个方法体！意味着不会更新数据库
                //  cartAsyncService.updateCartInfo(cartInfoLogin);
            }else {
                //剩下的skuId不同的商品
                //赋值登陆的userId
                cartInfo.setUserId(userId);
                //  添加时间
                cartInfo.setCreateTime(new Timestamp(new Date().getTime()));
                cartInfo.setUpdateTime(new Timestamp(new Date().getTime()));
                cartInfoMapper.insert(cartInfo);
            }
        }
        //从数据库中获取到最新的合并数据，然后放入缓存
        List<CartInfo> cartInfoList = this.loadCartCache(userId);
        return cartInfoList;
    }

    /**
     * 获取已登录用户 或 临时用户 购物车
     * @param userId
     * @return
     */
    @Override
    public List<CartInfo> getCartList(String userId) {
        //声明一个返回的集合对象
        List<CartInfo> cartInfoList = new ArrayList<>();
        if(StringUtils.isEmpty(userId)) return cartInfoList;
        //根据用户id查询，先查缓存，缓存没有，再查数据库
        String cartKey = this.getCartKey(userId);
        //查缓存
        cartInfoList = redisTemplate.opsForHash().values(cartKey);
        if(!CollectionUtils.isEmpty(cartInfoList)){
            //购物车列表有显示顺序，按照商品的更新时间排序
            cartInfoList.sort(new Comparator<CartInfo>() {
                @Override
                public int compare(CartInfo o1, CartInfo o2) {
                   return DateUtil.truncatedCompareTo(o2.getUpdateTime(),o1.getUpdateTime(), Calendar.SECOND);
                }
            });
            return cartInfoList;
        }else {
            //缓存中没数据
            cartInfoList = this.loadCartCache(userId);
            return cartInfoList;
        }
    }

    /**
     * 更新选中状态
     * @param userId
     * @param isChecked
     * @param skuId
     */
    @Override
    public void checkCart(String userId, Integer isChecked, Long skuId) {
        //修改数据库
        cartAsyncService.checkCart(userId,isChecked,skuId);
        //修改缓存
        String cartKey = this.getCartKey(userId);
        if(redisTemplate.opsForHash().hasKey(cartKey,skuId.toString())){
            //存在对应skuId的 field
            CartInfo cartInfoUpd = (CartInfo) redisTemplate.opsForHash().get(cartKey,skuId.toString());
            cartInfoUpd.setIsChecked(isChecked);
            //更新缓存
            redisTemplate.opsForHash().put(cartKey,skuId.toString(),cartInfoUpd);
            //设置过期时间
            this.setCartKeyExpire(cartKey);
        }
    }

    /**
     * 删除购物车
     * @param skuId
     * @param userId
     */
    @Override
    public void deleteCart(Long skuId, String userId) {
        String cartKey = this.getCartKey(userId);
        //异步删除数据库
        cartAsyncService.deleteCartInfo(userId,skuId);
        //删除缓存
        if(redisTemplate.opsForHash().hasKey(cartKey,skuId.toString())){
            redisTemplate.opsForHash().delete(cartKey,skuId.toString());
        }
    }

    /**
     * 根据用户Id 查询已选中购物车列表
     *
     * @param userId
     * @return
     */
    @Override
    public List<CartInfo> getCartCheckedList(String userId) {
        //缓存中有数据，直接从缓存查
        String cartKey = this.getCartKey(userId);
        List<CartInfo> cartInfoList = redisTemplate.opsForHash().values(cartKey);
        if(!CollectionUtils.isEmpty(cartInfoList)){
            cartInfoList = cartInfoList.stream()
                    .filter(cartInfo -> cartInfo.getIsChecked() == 1)
                    .collect(Collectors.toList());
        }

        return cartInfoList;
    }

    /**
     * 查询数据库
     * 通过userId查询购物车，并放入缓存
     * @param userId
     * @return
     */
    public List<CartInfo> loadCartCache(String userId) {
        QueryWrapper<CartInfo> cartInfoQueryWrapper = new QueryWrapper<>();
        cartInfoQueryWrapper.eq("user_id",userId);
        List<CartInfo> cartInfoList = cartInfoMapper.selectList(cartInfoQueryWrapper);
        if (CollectionUtils.isEmpty(cartInfoList)) {
            return cartInfoList;
        }
        //按照商品更新时间排序
        cartInfoList.sort(new Comparator<CartInfo>() {
            @Override
            public int compare(CartInfo o1, CartInfo o2) {
                return DateUtil.truncatedCompareTo(o2.getUpdateTime(),o1.getUpdateTime(),Calendar.SECOND);
            }
        });
        //将数据库中的数据放入缓存
        Map<String,CartInfo> map = new HashMap<>();
        for (CartInfo cartInfo : cartInfoList) {
            //缓存中没有数据 可能是一年后缓存过期， 商品价格可能会有改变，所以要更新一下商品实时价格
            BigDecimal skuPrice = productFeignClient.getSkuPrice(cartInfo.getSkuId());
            cartInfo.setSkuPrice(skuPrice);
            //  key = user:userId:cart ,谁的购物车 field = skuId value = cartInfo
            map.put(cartInfo.getSkuId().toString(),cartInfo);
        }
        String cartKey = this.getCartKey(userId);
        redisTemplate.opsForHash().putAll(cartKey,map);
        //设置过期时间
        this.setCartKeyExpire(cartKey);
        return cartInfoList;
    }

    /**
     * 设置redis数据的过期时间
     * @param cartKey
     */
    private void setCartKeyExpire(String cartKey) {
        redisTemplate.expire(cartKey,RedisConst.USER_CART_EXPIRE, TimeUnit.SECONDS);
    }

    /**
     * 获取购物车的key
     * @param userId
     * @return
     */
    private String getCartKey(String userId) {
        //定义key user:userId:cart
        return RedisConst.USER_KEY_PREFIX + userId + RedisConst.USER_CART_KEY_SUFFIX;
    }
}
