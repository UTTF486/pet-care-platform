package com.petcare.controller;

import com.petcare.common.Result;
import com.petcare.service.StatsService;
import com.petcare.vo.ServiceRankVO;
import com.petcare.vo.StatsOverviewVO;
import com.petcare.vo.TrendPointVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台 - 数据看板。
 * 三个接口分别对应：指标卡、趋势折线图、服务排行柱状图。
 */
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** 顶部指标卡（60 秒缓存） */
    @GetMapping("/overview")
    public Result<StatsOverviewVO> overview() {
        return Result.success(statsService.overview());
    }

    /**
     * 趋势报表。
     *
     * @param type  day / week / month
     * @param range 统计周期数
     */
    @GetMapping("/trend")
    public Result<List<TrendPointVO>> trend(@RequestParam(defaultValue = "day") String type,
                                            @RequestParam(defaultValue = "7") int range) {
        return Result.success(statsService.trend(type, range));
    }

    /** 服务项目排行 */
    @GetMapping("/ranking")
    public Result<List<ServiceRankVO>> ranking(@RequestParam(defaultValue = "day") String type,
                                               @RequestParam(defaultValue = "7") int range,
                                               @RequestParam(defaultValue = "5") int limit) {
        return Result.success(statsService.ranking(type, range, limit));
    }
}
