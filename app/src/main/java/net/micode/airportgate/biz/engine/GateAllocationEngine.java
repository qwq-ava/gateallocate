package net.micode.airportgate.biz.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 机位自动分配引擎。
 *
 * 对齐《软件设计规格说明书》：
 *   - 1.2 技术约束：核心算法用 Java 实现，模型为区间图着色（Interval Graph Coloring）结合回溯法（Backtracking）；
 *   - 4.1.2 停机位分配用例：queryAvailableGates + queryFlightsByDate
 *     -&gt; startAutoAllocation(date) -&gt; saveAllocationList -&gt; AllocationResult；
 *   - 数据约束：同一停机位在同一时段只能分配给一个航班（强一致性）。
 *
 * 算法思想：
 *   1. 将「航班-机位匹配」抽象为区间图着色问题——每个航班是一段占用区间，
 *      每个机位是一种颜色，同色（同机位）区间不得重叠；
 *   2. 贪心阶段：航班按开始时刻升序（区间图着色标准顺序），依次尝试放入
 *      满足约束的最高优先级机位；
 *   3. 回溯阶段：贪心失败时，尝试把目标机位上已占用的窄体航班「腾挪」到其他
 *      可行机位（深度受限回溯 + 剪枝），为当前航班让出资源；
 *   4. 约束条件：时段不重叠、同机位相邻航班安全间隔、宽体优先廊桥（不足时降级远机位）。
 *
 * 本类仅依赖 Java 标准库，不依赖 Android/Compose，可在 JVM 上独立单元测试。
 */
public final class GateAllocationEngine {

    /** 同机位相邻航班的安全间隔（分钟）：滑行、保洁、加油等保障时间 */
    public static final int SAFETY_GAP = 15;

    /** 回溯最大深度（演示规模 1 层足够，防止指数级组合爆炸） */
    private static final int MAX_BACKTRACK_DEPTH = 1;

    private GateAllocationEngine() {
    }

    // ========================= 输入模型 =========================

    /** 机位输入（对齐 GateForEngine：编号 / 类型 / 可停机型系列 / 安全间隔 / 可用时段 / 状态） */
    public static final class GateIn {
        public final String code;                // 机位编号
        public final boolean isBridge;           // true=廊桥机位 false=远机位
        public final List<String> aircraftClasses; // 可停靠机型系列名列表，如 ["A330","A320"]
        public final int minInterval;            // 该机位安全间隔（分钟），取代全局常量
        public final int openMin;                // 可用时段起（当日分钟 0..1439）
        public final int closeMin;               // 可用时段止（当日分钟 0..1439）
        public final String status;              // 可用 / 维护 / 停用

        public GateIn(String code, boolean isBridge, List<String> aircraftClasses,
                      int minInterval, int openMin, int closeMin, String status) {
            this.code = code;
            this.isBridge = isBridge;
            this.aircraftClasses = aircraftClasses;
            this.minInterval = minInterval;
            this.openMin = openMin;
            this.closeMin = closeMin;
            this.status = status;
        }
    }

    /** 机型系列名：截到第一个 '-' 或机型数字后的字母，如 "A330-300"->"A330"、"A320neo"->"A320" */
    public static String seriesOf(String aircraft) {
        if (aircraft == null) return "";
        String s = aircraft.trim();
        int dash = s.indexOf('-');
        if (dash > 0) s = s.substring(0, dash);   // A330-300 -> A330
        // A320neo -> A320：数字之后出现的字母截断
        boolean seenDigit = false;
        int cut = s.length();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) seenDigit = true;
            else if (seenDigit && Character.isLetter(c)) { cut = i; break; }
        }
        return s.substring(0, cut).trim();
    }

    /**
     * 机型兼容：把机位允许的机型与航班机型**都归一到系列名**再比较。
     *
     * 机位维护界面存的是完整型号（"A350-900"、"B737 MAX 8"、"A320neo"），
     * 航班侧通常也是完整型号，因此两边都先 seriesOf() 归一后比较：
     *   "A350-900"   与 "A350"      -> A350 / A350   匹配
     *   "A320neo"    与 "A320"      -> A320 / A320   匹配
     *   "B737 MAX 8" 与 "B737-800"  -> B737 / B737   匹配
     * 完全相同的字符串直接判定通过（优先，避免归一误差）。
     * 机位未配置机型（空列表）表示不限制。
     */
    public static boolean compatible(GateIn g, FlightIn f) {
        if (g.aircraftClasses == null || g.aircraftClasses.isEmpty()) return true;
        String flight = f.aircraft == null ? "" : f.aircraft.trim();
        String series = seriesOf(flight);
        for (String c : g.aircraftClasses) {
            if (c == null) continue;
            String t = c.trim();
            if (t.isEmpty()) continue;
            if (t.equalsIgnoreCase(flight)) return true;            // 完整型号完全相同
            if (seriesOf(t).equalsIgnoreCase(series)) return true;  // 归一到系列名后一致
        }
        return false;
    }

    /** 航班输入（FlightInfo：航班号 / 机型 / 占用起止分钟） */
    public static final class FlightIn {
        public final String flightNo;
        public final String aircraft;
        public final int startMin;
        public final int endMin;

        public FlightIn(String flightNo, String aircraft, int startMin, int endMin) {
            this.flightNo = flightNo;
            this.aircraft = aircraft;
            this.startMin = startMin;
            this.endMin = endMin;
        }

        /** 宽体机判定：宽体仅可停廊桥 */
        public boolean isWideBody() {
            return aircraft.startsWith("A330") || aircraft.startsWith("A350")
                    || aircraft.startsWith("B787") || aircraft.startsWith("B777")
                    || aircraft.startsWith("B747") || aircraft.startsWith("A380");
        }
    }

    /** 既有占用（增量分配时传入；全量重排传空列表） */
    public static final class Occupancy {
        public final String gate;
        public final FlightIn flight;

        public Occupancy(String gate, FlightIn flight) {
            this.gate = gate;
            this.flight = flight;
        }
    }

    // ========================= 输出模型 =========================

    /** 单条分配结果（GateAllocation：航班号 / 机位 / 起止 / 机位类型） */
    public static final class Assignment {
        public final String flightNo;
        public final String gate;
        public final int startMin;
        public final int endMin;
        public final boolean bridge;

        public Assignment(String flightNo, String gate, int startMin, int endMin, boolean bridge) {
            this.flightNo = flightNo;
            this.gate = gate;
            this.startMin = startMin;
            this.endMin = endMin;
            this.bridge = bridge;
        }
    }

    /** 分配结果汇总（AllocationResult） */
    public static final class Result {
        public final List<Assignment> assignments;  // 成功分配
        public final List<String> failedFlightNos;  // 分配失败航班号
        public final int moveCount;                 // 回溯腾挪次数
        public final long elapsedMs;                // 算法耗时

        public Result(List<Assignment> assignments, List<String> failedFlightNos,
                      int moveCount, long elapsedMs) {
            this.assignments = assignments;
            this.failedFlightNos = failedFlightNos;
            this.moveCount = moveCount;
            this.elapsedMs = elapsedMs;
        }
    }

    // ========================= 内部结构 =========================

    /** 一次放置：航班 + 机位 + 占用区间（腾挪时同步更新 gate） */
    private static final class Placement {
        final FlightIn flight;
        String gate;
        final int[] interval;

        Placement(FlightIn flight, String gate) {
            this.flight = flight;
            this.gate = gate;
            this.interval = new int[]{flight.startMin, flight.endMin};
        }
    }

    // ========================= 主入口 =========================

    /**
     * 执行自动分配（对应文档 startAutoAllocation(date)）。
     *
     * @param flights  当天待分配航班列表（全量重排时传入全部航班）
     * @param gates    可用机位列表
     * @param existing 既有占用；全量重排传空列表
     * @return 分配结果
     */
    public static Result startAutoAllocation(List<FlightIn> flights, List<GateIn> gates,
                                             List<Occupancy> existing) {
        long t0 = System.currentTimeMillis();

        // 机位 -> 已放置记录（放置与腾挪都通过此表维护，保证结果与实际一致）
        Map<String, List<Placement>> byGate = new HashMap<>();
        for (GateIn g : gates) {
            byGate.put(g.code, new ArrayList<Placement>());
        }

        // 既有占用直接落入占用表
        if (existing != null) {
            for (Occupancy o : existing) {
                List<Placement> list = byGate.get(o.gate);
                if (list != null) {
                    list.add(new Placement(o.flight, o.gate));
                }
            }
        }

        // 区间图着色：最受约束的航班先行——宽体（仅廊桥优先、可降级远机位）
        // 排在窄体之前，避免窄体先占满廊桥导致宽体无处可停；
        // 同类型内按开始时刻升序（区间图着色标准顺序），同时开始则长者优先。
        List<FlightIn> sorted = new ArrayList<>(flights);
        Collections.sort(sorted, new Comparator<FlightIn>() {
            @Override
            public int compare(FlightIn a, FlightIn b) {
                if (a.isWideBody() != b.isWideBody()) {
                    return a.isWideBody() ? -1 : 1;
                }
                if (a.startMin != b.startMin) {
                    return Integer.compare(a.startMin, b.startMin);
                }
                return Integer.compare(b.endMin, a.endMin); // 同时开始则长者优先
            }
        });

        List<String> failed = new ArrayList<>();
        int[] moveCount = {0};

        // 贪心 + 回溯
        for (FlightIn f : sorted) {
            String gate = pickGate(f, gates, byGate, null);
            if (gate != null) {
                byGate.get(gate).add(new Placement(f, gate));
                continue;
            }
            // 贪心失败：回溯腾挪，把目标机位上的窄体航班移到别处
            String freed = backtrackFree(f, gates, byGate, 0, moveCount);
            if (freed != null) {
                byGate.get(freed).add(new Placement(f, freed));
            } else {
                failed.add(f.flightNo);
            }
        }

        // 汇总结果
        List<Assignment> placed = new ArrayList<>();
        for (Map.Entry<String, List<Placement>> e : byGate.entrySet()) {
            for (Placement p : e.getValue()) {
                boolean bridge = false;
                for (GateIn g : gates) {
                    if (g.code.equals(p.gate)) {
                        bridge = g.isBridge;
                        break;
                    }
                }
                placed.add(new Assignment(p.flight.flightNo, p.gate,
                        p.interval[0], p.interval[1], bridge));
            }
        }

        long elapsed = System.currentTimeMillis() - t0;
        return new Result(placed, failed, moveCount[0], elapsed);
    }

    // ========================= 内部实现 =========================

    /** 为航班挑选满足约束的最高优先级机位；exclude 表示排除某机位（腾挪时用） */
    private static String pickGate(FlightIn f, List<GateIn> gates,
                                   Map<String, List<Placement>> byGate, String exclude) {
        List<GateIn> candidates = new ArrayList<>(gates);
        if (exclude != null) {
            candidates.removeIf(g -> g.code.equals(exclude));
        }
        // 优先级策略（贴近真实调度，且与原数据可行解一致）：
        //   宽体：廊桥优先，廊桥不足时降级远机位；
        //   窄体：远机位优先，远机位不足时降级廊桥；
        //   同类型内按机位编号排序（原设计按保障等级，最终文档已去除 level 字段）。
        final boolean wide = f.isWideBody();
        Collections.sort(candidates, new Comparator<GateIn>() {
            @Override
            public int compare(GateIn a, GateIn b) {
                if (a.isBridge != b.isBridge) {
                    boolean aFirst = wide ? a.isBridge : !a.isBridge;
                    boolean bFirst = wide ? b.isBridge : !b.isBridge;
                    if (aFirst != bFirst) {
                        return aFirst ? -1 : 1;
                    }
                }
                return a.code.compareTo(b.code);
            }
        });
        for (GateIn g : candidates) {
            if (!"可用".equals(g.status)) continue;                       // status != 可用 跳过
            if (!compatible(g, f)) continue;                              // 机型系列名不兼容跳过
            if (f.startMin < g.openMin || f.endMin > g.closeMin) continue; // 超出可用时段跳过
            if (isFree(byGate.get(g.code), f.startMin, f.endMin, g.minInterval)) {
                return g.code;
            }
        }
        return null;
    }

    /** 检查机位在 [start - gap, end + gap] 内是否空闲（含该机位的安全间隔） */
    private static boolean isFree(List<Placement> placements, int start, int end, int gap) {
        int from = start - gap;
        int to = end + gap;
        if (placements == null) {
            return true;
        }
        for (Placement p : placements) {
            // 半开区间重叠判定：[from, to) 与 [p.from, p.to) 相交即冲突
            if (from < p.interval[1] && p.interval[0] < to) {
                return false;
            }
        }
        return true;
    }

    /**
     * 回溯腾挪：当前航班无可用机位时，尝试把冲突机位上已占用的航班移到
     * 其他可行机位，从而腾出资源。深度受限，防止组合爆炸。
     *
     * @return 腾挪成功后空出来的机位编号；失败返回 null
     */
    private static String backtrackFree(FlightIn f, List<GateIn> gates,
                                        Map<String, List<Placement>> byGate,
                                        int depth, int[] moveCount) {
        if (depth >= MAX_BACKTRACK_DEPTH) {
            return null;
        }
        for (GateIn target : gates) {
            List<Placement> placements = byGate.get(target.code);
            if (placements == null || placements.isEmpty()) {
                continue;
            }
            // 遍历快照，避免在对 placements 增删时触发 ConcurrentModificationException
            for (Placement p : new ArrayList<>(placements)) {
                // 仅当被占时段与当前航班（含该机位安全间隔）冲突时才值得腾挪
                int gap = target.minInterval;
                if (!(f.startMin - gap < p.interval[1]
                        && p.interval[0] < f.endMin + gap)) {
                    continue;
                }
                // 为被占航班另寻机位（排除当前机位；pickGate 内部按机型偏好排序，
                // 宽体可降级远机位，窄体可降级廊桥）
                String alt = pickGate(p.flight, gates, byGate, target.code);
                if (alt != null) {
                    placements.remove(p);
                    byGate.get(alt).add(p);
                    p.gate = alt; // 同步更新机位，保证结果与实际一致
                    moveCount[0]++;
                    // 腾挪后再次确认当前航班在该机位空闲（含安全间隔）；
                    // 若仍冲突则回滚本次腾挪，继续尝试其它目标机位
                    if (isFree(byGate.get(target.code), f.startMin, f.endMin, target.minInterval)) {
                        return target.code;
                    } else {
                        byGate.get(alt).remove(p);
                        placements.add(p);
                        p.gate = target.code;
                        moveCount[0]--;
                    }
                }
            }
        }
        return null;
    }
}
