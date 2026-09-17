package cn.edu.buaa.hzcampus.model.dto

import kotlin.test.*

class TeacherExtractionTest {
  @Test
  fun extractsTeachersFromEveryConfirmedWeeksAndTeachersForm() {
    // 研究生 GSMIS／历史格式：ZCMC 与 JSXM 用一个空格拼接。
    assertEquals("教师甲,教师乙", extractTeachers("2,4周 教师甲,教师乙"))
    // GSMIS 没有周次名称时该字段只有教师。
    assertEquals("示例教师", extractTeachers("示例教师"))
    // 本科教务把周次和教师用 /、空白或括号里的单双周分开。
    assertEquals("张三", extractTeachers("1-16周/张三"))
    assertEquals("张三", extractTeachers("1-16周 张三"))
    assertEquals("张三", extractTeachers("第1-16周（单）张三"))
    assertEquals("张三", extractTeachers("张三/1-16周"))
    assertEquals("张三", extractTeachers("1-16周张三"))
    assertEquals("张三", extractTeachers("第1-2节张三"))
    assertEquals("张三、李四", extractTeachers("1-16周/张三、李四"))
  }

  @Test
  fun keepsTeacherNamesThatLookLikeWeekWords() {
    // 「周」「单」也是姓氏用字，没有数字锚点的片段不能被当成周次。
    assertEquals("周建国", extractTeachers("周建国"))
    assertEquals("周建国", extractTeachers("1-16周 周建国"))
    assertEquals("单田芳", extractTeachers("单田芳"))
  }

  @Test
  fun returnsNullWhenThereIsNoTeacher() {
    assertNull(extractTeachers(null))
    assertNull(extractTeachers(""))
    assertNull(extractTeachers("   "))
    assertNull(extractTeachers("1-16周"))
    assertNull(extractTeachers("2,4周"))
    assertNull(extractTeachers("第1-16周（单）"))
    assertNull(extractTeachers("单周"))
  }
}
