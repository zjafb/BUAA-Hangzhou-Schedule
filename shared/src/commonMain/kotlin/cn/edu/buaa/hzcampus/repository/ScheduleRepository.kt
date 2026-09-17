package cn.edu.buaa.hzcampus.repository

import cn.edu.buaa.hzcampus.api.feature.ScheduleApi
import cn.edu.buaa.hzcampus.model.dto.*
import com.russhwolf.settings.Settings
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 一次完整导入的学期；所有周次成功后才替换本地版本。 */
@Serializable
data class SemesterSchedule(
    val terms: List<Term>,
    val termCode: String,
    val weeks: List<Week>,
    val schedules: Map<Int, WeeklySchedule>,
    val updatedAt: String = "",
)

object ScheduleStore {
  private val revision = MutableStateFlow(0L)
  val changes = revision.asStateFlow()
  private var backing: Settings? = null
  var settings: Settings
    get() = backing ?: Settings().also { backing = it }
    set(value) {
      backing = value
    }

  private val json = Json { ignoreUnknownKeys = true }
  private const val ACCOUNT = "schedule_account"

  fun account(): String? = settings.getStringOrNull(ACCOUNT)

  fun useAccount(account: String) {
    require(account.isNotBlank())
    settings.putString(ACCOUNT, account)
    revision.update { it + 1 }
  }

  fun forgetAccount() {
    settings.remove(ACCOUNT)
    revision.update { it + 1 }
  }

  fun read(account: String): List<SemesterSchedule> =
      ScheduleSnapshotStorage(settings).read(account)?.let {
        json.decodeFromString<List<SemesterSchedule>>(it)
      } ?: emptyList()

  fun write(account: String, semesters: List<SemesterSchedule>) {
    ScheduleSnapshotStorage(settings).write(account, json.encodeToString(semesters))
    revision.update { it + 1 }
  }

  fun hasSavedSchedule(): Boolean =
      runCatching { account()?.let { read(it).isNotEmpty() } == true }.getOrDefault(false)
}

/** 默认在线查询，失败时回退到已手动本地化的课表；仅 update 写入持久存储。 */
class ScheduleRepository(
    private val api: ScheduleApi = ScheduleApi(),
    private val account: () -> String? = ScheduleStore::account,
    private val read: (String) -> List<SemesterSchedule> = ScheduleStore::read,
    private val write: (String, List<SemesterSchedule>) -> Unit = ScheduleStore::write,
    private val today: () -> LocalDate = {
      Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Shanghai")).date
    },
) {
  private val updateMutex = Mutex()

  suspend fun loadTerms(offlineOnly: Boolean = false): Result<List<Term>> =
      onlineOrSaved(offlineOnly, { api.getTerms() }, ::terms)

  suspend fun loadWeeks(code: String, offlineOnly: Boolean = false): Result<List<Week>> =
      onlineOrSaved(
          offlineOnly,
          { api.getWeeks(code).mapCatching(::normalizeWeeks) },
          { weeks(code) },
      )

  suspend fun loadWeekly(
      code: String,
      week: Int,
      offlineOnly: Boolean = false,
  ): Result<WeeklySchedule> =
      onlineOrSaved(offlineOnly, { api.getWeeklySchedule(code, week) }, { weekly(code, week) })

  suspend fun loadTodayClasses(offlineOnly: Boolean = false): Result<List<TodayClass>> =
      onlineOrSaved(
          offlineOnly,
          { api.getTodaySchedule().mapCatching(::withSavedTeachers) },
          ::todayClasses,
      )

  /**
   * 在线今日摘要只有课程名、时间、地点，教师只存在于整学期课表的「周次/教师」字段里。
   *
   * 用已本地化且覆盖今天的周课表按「课程名 + 星期」补全缺失的教师；没有本地课表时原样返回，不联网，也不改变其它字段。
   */
  private fun withSavedTeachers(classes: List<TodayClass>): List<TodayClass> {
    if (classes.isEmpty() || classes.all { it.teacher != null }) return classes
    val owner = account() ?: return classes
    val date = today()
    val schedule =
        runCatching { weeklyScheduleCovering(read(owner), date) }.getOrNull() ?: return classes
    val day = date.dayOfWeek.ordinal + 1
    val teachers =
        schedule.arrangedList
            .filter { it.dayOfWeek == day }
            .mapNotNull { course ->
              extractTeachers(course.weeksAndTeachers)?.let { course.courseName to it }
            }
            .toMap()
    return classes.map { it.copy(teacher = it.teacher ?: teachers[it.bizName]) }
  }

  private suspend fun <T> onlineOrSaved(
      offlineOnly: Boolean,
      online: suspend () -> Result<T>,
      local: () -> Result<T>,
  ): Result<T> {
    if (offlineOnly) return local()
    val owner = account()
    val result =
        try {
          online()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Result.failure(e)
        }
    currentCoroutineContext().ensureActive()
    (result.exceptionOrNull() as? CancellationException)?.let { throw it }
    if (account() != owner) return Result.failure(IllegalStateException("账号已切换，请重新加载课表"))
    // 无本地数据时保留真正的网络错误，不用“尚未导入”掩盖它。
    return if (result.isSuccess) result else local().takeIf { it.isSuccess } ?: result
  }

  private fun saved(): List<SemesterSchedule> = read(account() ?: error("请先登录并导入课表"))

  private fun normalizeWeeks(weeks: List<Week>): List<Week> =
      weeks.map {
        it.copy(startDate = normalizeDate(it.startDate), endDate = normalizeDate(it.endDate))
      }

  private fun normalizeDate(raw: String): String {
    // 本科接口返回 yyyy-MM-dd HH:mm:ss；缓存统一存日期，保证校验及离线范围比较正确。
    val date = raw.trim().substringBefore(' ').substringBefore('T')
    val match = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").matchEntire(date)
    requireNotNull(match) { "课表日期格式无效，未覆盖本地数据" }
    val (year, month, day) = match.destructured
    return LocalDate(year.toInt(), month.toInt(), day.toInt()).toString()
  }

  private fun semester(code: String) =
      saved().firstOrNull { it.termCode == code } ?: error("此学期尚未导入，请点击课表本地化")

  fun terms(): Result<List<Term>> = runCatching {
    val snapshots = saved()
    check(snapshots.isNotEmpty()) { "尚未导入课表，请进入课表页点击课表本地化" }
    val current = snapshots.first().terms.firstOrNull { it.selected }?.itemCode
    snapshots
        .flatMap { it.terms }
        .distinctBy { it.itemCode }
        .map { it.copy(selected = it.itemCode == current) }
  }

  fun weeks(code: String): Result<List<Week>> = runCatching {
    val date = today().toString()
    semester(code).weeks.map { it.copy(curWeek = date >= it.startDate && date <= it.endDate) }
  }

  fun weekly(code: String, week: Int): Result<WeeklySchedule> = runCatching {
    semester(code).schedules[week] ?: error("本地课表缺少此周，请点击课表本地化")
  }

  fun schedules(code: String): Result<Map<Int, WeeklySchedule>> = runCatching {
    semester(code).schedules
  }

  fun updatedAt(code: String?): String? =
      runCatching { saved().firstOrNull { it.termCode == code }?.updatedAt }.getOrNull()

  fun todayClasses(): Result<List<TodayClass>> = runCatching {
    val snapshots = saved()
    check(snapshots.isNotEmpty()) { "尚未导入课表，请进入课表页点击课表本地化" }
    val date = today()
    // 位图的尾部空周可能跨到下一学期，优先使用开学日期更晚的已保存学期。
    val snapshot =
        snapshots
            .filter {
              it.weeks.any { week ->
                date.toString() >= week.startDate && date.toString() <= week.endDate
              }
            }
            .maxByOrNull { it.weeks.minOf { week -> week.startDate } }
    val week =
        snapshot?.weeks?.firstOrNull {
          date.toString() >= it.startDate && date.toString() <= it.endDate
        }
    snapshot
        ?.schedules
        ?.get(week?.serialNumber)
        ?.arrangedList
        ?.filter { it.dayOfWeek == date.dayOfWeek.ordinal + 1 }
        .orEmpty()
        .sortedBy { it.beginTime ?: "" }
        .map { it.toTodayClass() }
  }

  /** 今天的日期（`yyyy-MM-dd`，按 Asia/Shanghai 计算），供提醒排程等离线场景使用。 */
  fun todayIsoDate(): String = today().toString()

  /**
   * 未来 [days] 天（含今天）的全部课程，按日期与开始时间排序；数据全部来自本地已保存的课表，不联网。
   *
   * 课前提醒用它一次排满未来若干天：只排「今天」的话，连续几天不打开 App 的那些天就不会有提醒。 课表未本地化或缓存损坏时返回失败，调用方自行降级。
   */
  fun upcomingClasses(days: Int = 7): Result<List<DatedClass>> = runCatching {
    require(days > 0) { "days 必须大于 0" }
    val snapshots = saved()
    check(snapshots.isNotEmpty()) { "尚未导入课表，请进入课表页点击课表本地化" }
    savedAgenda(snapshots, today(), days).map {
      DatedClass(it.date.toString(), it.course.toTodayClass())
    }
  }

  /** 把课表里的一门课转成提醒/首页使用的摘要 DTO。 */
  private fun CourseClass.toTodayClass(): TodayClass =
      TodayClass(
          courseName,
          placeName,
          listOfNotNull(beginTime, endTime).joinToString("-"),
          courseName,
          extractTeachers(weeksAndTeachers),
      )

  suspend fun update(code: String? = null): Result<SemesterSchedule> =
      updateMutex.withLock {
        try {
          val owner = account() ?: error("请先登录后更新课表")
          val imported = api.importSemester(code).getOrThrow()
          val snapshot = imported.copy(weeks = normalizeWeeks(imported.weeks))
          currentCoroutineContext().ensureActive()
          check(account() == owner) { "账号已切换，请重新本地化课表" }
          check(code == null || snapshot.termCode == code) { "返回的课表学期与所选学期不一致，未覆盖本地数据" }
          check(snapshot.terms.any { it.itemCode == snapshot.termCode }) { "课表缺少学期信息" }
          check(snapshot.weeks.map { it.serialNumber }.toSet() == snapshot.schedules.keys) {
            "课表周次不完整，未覆盖本地数据"
          }
          // 本科 WeeklySchedule.code 实际为学号；研究生适配才填学期代码，不能统一比较。
          // 学期归属由请求参数、snapshot.termCode 和下面的 Week.term 校验。
          check(snapshot.weeks.distinctBy { it.serialNumber }.size == snapshot.weeks.size) {
            "课表周次重复，未覆盖本地数据"
          }
          snapshot.weeks.forEach {
            check(
                it.term == snapshot.termCode &&
                    it.serialNumber > 0 &&
                    LocalDate.parse(it.startDate) <= LocalDate.parse(it.endDate)
            ) {
              "课表日期无效，未覆盖本地数据"
            }
          }
          val updated =
              snapshot.copy(
                  updatedAt =
                      Clock.System.now()
                          .toLocalDateTime(TimeZone.of("Asia/Shanghai"))
                          .toString()
                          .take(16)
                          .replace('T', ' ')
              )
          write(owner, listOf(updated) + read(owner).filterNot { it.termCode == updated.termCode })
          Result.success(updated)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Result.failure(e)
        }
      }
}
