package com.antigravity.assetmanager.model

/**
 * 구글 시트의 다양한 컬럼명(한글/영문/약어)을 표준 필드로 매핑하는 객체지향 파서
 */
class AliasMapper {
    private val aliases: Map<String, List<String>> = mapOf(
        "asset_number" to listOf("assetnumber", "자산번호", "관리번호", "assetno", "no", "관리no"),
        "in_user" to listOf("inuser", "사용자id", "사번", "id", "cjid", "user_id", "인사번호", "in_user"),
        "user_name" to listOf("username", "사용자", "성함", "성명", "이름", "name", "user"),
        "department" to listOf("department", "부서", "소속", "part", "팀", "팀명", "부서명"),
        "model_name" to listOf("modelname", "모델명", "모델", "품명", "자산명", "기종", "모델코드", "model"),
        "serial_number" to listOf("serialnumber", "sn", "s/n", "시리얼", "제조번호", "serial_number"),
        "category" to listOf("category", "카테고리", "분류", "자산분류"),
        "state" to listOf("state", "상태", "구분", "자산구분"),
        "status" to listOf("status", "실사상태", "진행상태"),
        "inspection_time" to listOf("inspectiontime", "실사시간", "점검시간", "시간"),
        "ex_user" to listOf("ex_user", "이전에사용하던사람", "이전사용자", "asset_in_user", "prev_user"),
        "cj_id" to listOf("cjid", "사번", "id", "cj_id", "사용자id", "사용자사번"),
        "date" to listOf("date", "업무일자", "일자", "날짜", "timestamp", "수정일", "변경일", "작업일자", "시간", "수정시간", "생성일"),
        "note" to listOf("note", "메모", "비고", "사항")
    )

    private val normalizedAliasMap: Map<String, List<String>> = aliases.mapValues { entry ->
        entry.value.map { normalize(it) }
    }

    private fun normalize(str: String): String =
        str.lowercase().replace(" ", "").replace("_", "").trim()

    fun getValue(rowMap: Map<String, String>, targetKey: String): String? {
        val targetAliases = normalizedAliasMap[targetKey] ?: listOf(normalize(targetKey))
        val entry = rowMap.entries.firstOrNull { (k, _) ->
            targetAliases.contains(normalize(k))
        }
        return entry?.value?.trim()
    }
}
