package com.autoschedule.schedule.service;

import com.autoschedule.crew.domain.CrewJoinStatus;
import com.autoschedule.crew.domain.CrewRole;
import com.autoschedule.crew.domain.CrewStatus;
import com.autoschedule.crew.repository.CrewRepository;
import com.autoschedule.global.exception.ApiException;
import com.autoschedule.global.exception.ErrorCode;
import com.autoschedule.member.domain.Member;
import com.autoschedule.member.repository.MemberRepository;
import com.autoschedule.schedule.domain.ConfirmedScheduleAssignment;
import com.autoschedule.schedule.domain.ConfirmedScheduleAssignmentStatus;
import com.autoschedule.schedule.domain.ConfirmedWeekSchedule;
import com.autoschedule.schedule.domain.ConfirmedWeekScheduleStatus;
import com.autoschedule.schedule.domain.ScheduleGenerationRun;
import com.autoschedule.schedule.domain.ScheduleGenerationRunStatus;
import com.autoschedule.schedule.domain.SchedulePreview;
import com.autoschedule.schedule.domain.SchedulePreviewStatus;
import com.autoschedule.schedule.dto.ConfirmWeekScheduleRequest;
import com.autoschedule.schedule.dto.ConfirmedWeekScheduleResponse;
import com.autoschedule.schedule.dto.ManualScheduleAssignmentDeleteResponse;
import com.autoschedule.schedule.dto.ManualScheduleAssignmentRequest;
import com.autoschedule.schedule.dto.ManualScheduleAssignmentResponse;
import com.autoschedule.schedule.dto.ScheduleGenerationRunResponse;
import com.autoschedule.schedule.dto.SchedulePreviewResponse;
import com.autoschedule.schedule.generator.ScheduleCandidate;
import com.autoschedule.schedule.generator.ScheduleCandidateDay;
import com.autoschedule.schedule.generator.ScheduleCandidateGenerationCommand;
import com.autoschedule.schedule.generator.ScheduleCandidateGenerationResult;
import com.autoschedule.schedule.generator.ScheduleCandidateGenerator;
import com.autoschedule.schedule.generator.ScheduleCandidateTimeDetail;
import com.autoschedule.schedule.repository.ConfirmedScheduleAssignmentRepository;
import com.autoschedule.schedule.repository.ConfirmedWeekScheduleRepository;
import com.autoschedule.schedule.repository.ScheduleGenerationRunRepository;
import com.autoschedule.schedule.repository.SchedulePreviewRepository;
import com.autoschedule.schedulecondition.domain.Day;
import com.autoschedule.schedulecondition.domain.DayStatus;
import com.autoschedule.schedulecondition.domain.TimeDetail;
import com.autoschedule.schedulecondition.domain.TimeDetailStatus;
import com.autoschedule.schedulecondition.domain.WeekSchedule;
import com.autoschedule.schedulecondition.domain.WeekScheduleStatus;
import com.autoschedule.schedulecondition.repository.DayRepository;
import com.autoschedule.schedulecondition.repository.TimeDetailRepository;
import com.autoschedule.schedulecondition.repository.WeekScheduleRepository;
import com.autoschedule.workerselect.domain.WorkerSelectSubmission;
import com.autoschedule.workerselect.domain.WorkerSelectSubmissionStatus;
import com.autoschedule.workerselect.domain.WorkerUnavailableTimeDetail;
import com.autoschedule.workerselect.repository.WorkerSelectSubmissionRepository;
import com.autoschedule.workerselect.repository.WorkerUnavailableTimeDetailRepository;
import com.autoschedule.workplace.domain.WorkPlace;
import com.autoschedule.workplace.domain.WorkPlaceStatus;
import com.autoschedule.workplace.repository.WorkPlaceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자동 스케줄 생성, 미리보기 조회, 확정 처리를 담당한다.
 */
@Service
@RequiredArgsConstructor
public class ScheduleGenerationService {

    private static final String RANDOM_SELECT_LIMIT_ALGORITHM_VERSION = "RND_SELECT_LIMIT_V1";
    private static final String RANDOM_SELECT_LIMIT_ALGORITHM_SUFFIX = "+RND";

    private final MemberRepository memberRepository;
    private final WorkPlaceRepository workPlaceRepository;
    private final WeekScheduleRepository weekScheduleRepository;
    private final DayRepository dayRepository;
    private final TimeDetailRepository timeDetailRepository;
    private final CrewRepository crewRepository;
    private final WorkerSelectSubmissionRepository workerSelectSubmissionRepository;
    private final WorkerUnavailableTimeDetailRepository workerUnavailableTimeDetailRepository;
    private final ScheduleGenerationRunRepository scheduleGenerationRunRepository;
    private final SchedulePreviewRepository schedulePreviewRepository;
    private final ConfirmedWeekScheduleRepository confirmedWeekScheduleRepository;
    private final ConfirmedScheduleAssignmentRepository confirmedScheduleAssignmentRepository;
    private final ScheduleCandidateGenerator scheduleCandidateGenerator;
    private final ObjectMapper objectMapper;

    /**
     * 사장이 요청한 주간 스케줄 조건을 기준으로 가능한 후보 스케줄 JSON을 생성한다.
     */
    @Transactional
    public ScheduleGenerationRunResponse generateSchedulePreview(
            Long ownerMemberId,
            Long workPlaceId,
            Long weekScheduleId
    ) {
        Member owner = findActiveMember(ownerMemberId);
        WorkPlace workPlace = findOwnedActiveWorkPlace(workPlaceId, owner.getId());
        WeekSchedule weekSchedule = findActiveWeekSchedule(weekScheduleId, workPlace.getId());
        validateNoActiveGeneratedRun(weekSchedule.getId());

        List<Day> days = dayRepository.findByWeekSchedule_IdAndStatusAndDeletedAtIsNullOrderByDateAscIdAsc(
                weekSchedule.getId(),
                DayStatus.ACTIVE
        );
        List<TimeDetail> timeDetails = findActiveTimeDetails(days);
        List<TimeDetail> normalTimeDetails = findNormalTimeDetails(timeDetails);
        List<TimeDetail> randomTimeDetails = findRandomTimeDetails(timeDetails);
        List<Long> activeWorkerMemberIds = findActiveWorkerMemberIds(workPlace.getId());
        List<ScheduleCandidate> candidates;
        String algorithmVersion;

        if (normalTimeDetails.isEmpty()) {
            if (activeWorkerMemberIds.isEmpty()) {
                throw new ApiException(ErrorCode.CONFLICT, "자동 스케줄을 생성할 승인 근무자가 없습니다.");
            }
            candidates = List.of(new ScheduleCandidate(1, 0, List.of()));
            algorithmVersion = RANDOM_SELECT_LIMIT_ALGORITHM_VERSION;
        } else {
            List<Long> workerMemberIds = findSubmittedActiveWorkerMemberIds(
                    workPlace.getId(),
                    weekSchedule.getId(),
                    activeWorkerMemberIds
            );
            Map<Long, Set<Long>> unavailableWorkerIdsByTimeDetailId = findUnavailableWorkerIdsByTimeDetailId(
                    workPlace.getId(),
                    weekSchedule.getId(),
                    workerMemberIds
            );
            ScheduleCandidateGenerationResult generationResult = scheduleCandidateGenerator.generate(
                    new ScheduleCandidateGenerationCommand(
                            weekSchedule,
                            normalTimeDetails,
                            workerMemberIds,
                            unavailableWorkerIdsByTimeDetailId
                    )
            );
            candidates = generationResult.candidates();
            algorithmVersion = randomTimeDetails.isEmpty()
                    ? generationResult.algorithmVersion()
                    : generationResult.algorithmVersion() + RANDOM_SELECT_LIMIT_ALGORITHM_SUFFIX;
        }

        candidates = appendRandomAssignments(
                candidates,
                randomTimeDetails,
                activeWorkerMemberIds,
                weekSchedule.getMaxPersonalWorkCount()
        );

        if (candidates.isEmpty()) {
            scheduleGenerationRunRepository.save(ScheduleGenerationRun.failed(
                    weekSchedule,
                    workPlace.getId(),
                    owner.getId(),
                    algorithmVersion,
                    "조건을 만족하는 스케줄 후보가 없습니다."
            ));
            throw new ApiException(ErrorCode.CONFLICT, "조건을 만족하는 스케줄 후보가 없습니다.");
        }

        ScheduleGenerationRun run = scheduleGenerationRunRepository.save(ScheduleGenerationRun.generated(
                weekSchedule,
                workPlace.getId(),
                owner.getId(),
                candidates.size(),
                algorithmVersion
        ));
        SchedulePreview preview = schedulePreviewRepository.save(SchedulePreview.create(
                run,
                weekSchedule,
                writePreviewJson(candidates)
        ));

        return ScheduleGenerationRunResponse.from(run, preview);
    }

    /**
     * 기존 자동 생성 결과를 삭제 처리한 뒤 같은 조건으로 새 미리보기를 다시 생성한다.
     */
    @Transactional
    public ScheduleGenerationRunResponse regenerateSchedulePreview(
            Long ownerMemberId,
            Long workPlaceId,
            Long weekScheduleId
    ) {
        Member owner = findActiveMember(ownerMemberId);
        WorkPlace workPlace = findOwnedActiveWorkPlace(workPlaceId, owner.getId());
        WeekSchedule weekSchedule = findActiveWeekSchedule(weekScheduleId, workPlace.getId());

        markActiveGeneratedRunsAndPreviewsDeleted(weekSchedule.getId());
        return generateSchedulePreview(ownerMemberId, workPlaceId, weekScheduleId);
    }

    /**
     * 자동 생성 실행 이력에 속한 미리보기 JSON을 조회한다.
     */
    @Transactional(readOnly = true)
    public SchedulePreviewResponse getSchedulePreview(
            Long ownerMemberId,
            Long workPlaceId,
            Long weekScheduleId,
            Long scheduleGenerationRunId
    ) {
        Member owner = findActiveMember(ownerMemberId);
        WorkPlace workPlace = findOwnedActiveWorkPlace(workPlaceId, owner.getId());
        WeekSchedule weekSchedule = findActiveWeekSchedule(weekScheduleId, workPlace.getId());
        ScheduleGenerationRun run = findGeneratedRun(scheduleGenerationRunId, weekSchedule.getId());
        SchedulePreview preview = schedulePreviewRepository
                .findFirstByScheduleGenerationRun_IdAndWeekSchedule_IdAndStatusAndDeletedAtIsNullOrderByIdDesc(
                        run.getId(),
                        weekSchedule.getId(),
                        SchedulePreviewStatus.ACTIVE
                )
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "스케줄 미리보기를 찾을 수 없습니다."));

        return SchedulePreviewResponse.of(run, preview, readPreviewJson(preview.getPreviewData()));
    }

    /**
     * 사장이 선택한 미리보기 후보를 확정 스케줄과 확정 배정으로 전환한다.
     */
    @Transactional
    public ConfirmedWeekScheduleResponse confirmWeekSchedule(
            Long ownerMemberId,
            Long workPlaceId,
            Long weekScheduleId,
            ConfirmWeekScheduleRequest request
    ) {
        Member owner = findActiveMember(ownerMemberId);
        WorkPlace workPlace = findOwnedActiveWorkPlace(workPlaceId, owner.getId());
        WeekSchedule weekSchedule = findActiveWeekSchedule(weekScheduleId, workPlace.getId());

        if (confirmedWeekScheduleRepository.existsByWeekSchedule_IdAndStatusAndDeletedAtIsNull(
                weekSchedule.getId(),
                ConfirmedWeekScheduleStatus.ACTIVE
        )) {
            throw new ApiException(ErrorCode.CONFLICT, "이미 확정된 주간 스케줄이 존재합니다.");
        }

        ScheduleGenerationRun run = findGeneratedRun(request.scheduleGenerationRunId(), weekSchedule.getId());
        SchedulePreview preview = schedulePreviewRepository
                .findByIdAndScheduleGenerationRun_IdAndWeekSchedule_IdAndStatusAndDeletedAtIsNull(
                        request.schedulePreviewId(),
                        run.getId(),
                        weekSchedule.getId(),
                        SchedulePreviewStatus.ACTIVE
                )
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "스케줄 미리보기를 찾을 수 없습니다."));

        JsonNode candidate = findCandidate(preview.getPreviewData(), request.selectedCandidateNo());
        ConfirmedWeekSchedule confirmed = confirmedWeekScheduleRepository.save(ConfirmedWeekSchedule.create(
                weekSchedule,
                workPlace.getId(),
                run.getId(),
                preview.getId(),
                request.selectedCandidateNo(),
                owner.getId()
        ));

        List<ConfirmedScheduleAssignment> assignments = createAssignments(
                confirmed,
                workPlace.getId(),
                weekSchedule,
                candidate
        );
        confirmedScheduleAssignmentRepository.saveAll(assignments);
        preview.markConfirmed();

        return ConfirmedWeekScheduleResponse.of(confirmed, assignments.size());
    }

    /**
     * 확정된 주간 스케줄 안에 사장이 직접 근무 파트와 배정을 추가한다.
     */
    @Transactional
    public ManualScheduleAssignmentResponse createManualAssignment(
            Long ownerMemberId,
            Long workPlaceId,
            Long confirmedWeekScheduleId,
            ManualScheduleAssignmentRequest request
    ) {
        Member owner = findActiveMember(ownerMemberId);
        WorkPlace workPlace = findOwnedActiveWorkPlace(workPlaceId, owner.getId());
        ConfirmedWeekSchedule confirmed = findActiveConfirmedWeekSchedule(confirmedWeekScheduleId, workPlace.getId());
        Day day = findActiveDayInConfirmedSchedule(confirmed, request);

        validateManualAssignmentRequest(workPlace.getId(), day.getId(), request);

        TimeDetail timeDetail = timeDetailRepository.save(TimeDetail.create(
                day,
                request.workPartNo(),
                request.timeName(),
                request.workerMemberIds().size(),
                request.startTime(),
                request.closeTime(),
                request.restTime()
        ));
        saveManualAssignments(confirmed, workPlace.getId(), timeDetail, request.workerMemberIds());

        return ManualScheduleAssignmentResponse.of(
                confirmed.getId(),
                workPlace.getId(),
                confirmed.getWeekSchedule().getId(),
                timeDetail,
                request.workerMemberIds()
        );
    }

    /**
     * 확정된 근무 파트를 새 time_detail과 배정으로 교체한다.
     */
    @Transactional
    public ManualScheduleAssignmentResponse updateManualAssignment(
            Long ownerMemberId,
            Long workPlaceId,
            Long confirmedWeekScheduleId,
            Long timeDetailId,
            ManualScheduleAssignmentRequest request
    ) {
        Member owner = findActiveMember(ownerMemberId);
        WorkPlace workPlace = findOwnedActiveWorkPlace(workPlaceId, owner.getId());
        ConfirmedWeekSchedule confirmed = findActiveConfirmedWeekSchedule(confirmedWeekScheduleId, workPlace.getId());
        TimeDetail oldTimeDetail = findActiveTimeDetailInConfirmedSchedule(confirmed, timeDetailId);
        Day newDay = findActiveDayInConfirmedSchedule(confirmed, request);

        deleteTimeDetailAndAssignments(confirmed, oldTimeDetail);
        validateManualAssignmentRequest(workPlace.getId(), newDay.getId(), request);

        TimeDetail newTimeDetail = timeDetailRepository.save(TimeDetail.create(
                newDay,
                request.workPartNo(),
                request.timeName(),
                request.workerMemberIds().size(),
                request.startTime(),
                request.closeTime(),
                request.restTime()
        ));
        saveManualAssignments(confirmed, workPlace.getId(), newTimeDetail, request.workerMemberIds());

        return ManualScheduleAssignmentResponse.of(
                confirmed.getId(),
                workPlace.getId(),
                confirmed.getWeekSchedule().getId(),
                newTimeDetail,
                request.workerMemberIds()
        );
    }

    /**
     * 확정된 근무 파트와 해당 확정 배정을 삭제 처리한다.
     */
    @Transactional
    public ManualScheduleAssignmentDeleteResponse deleteManualAssignment(
            Long ownerMemberId,
            Long workPlaceId,
            Long confirmedWeekScheduleId,
            Long timeDetailId
    ) {
        Member owner = findActiveMember(ownerMemberId);
        WorkPlace workPlace = findOwnedActiveWorkPlace(workPlaceId, owner.getId());
        ConfirmedWeekSchedule confirmed = findActiveConfirmedWeekSchedule(confirmedWeekScheduleId, workPlace.getId());
        TimeDetail timeDetail = findActiveTimeDetailInConfirmedSchedule(confirmed, timeDetailId);

        int deletedAssignmentCount = deleteTimeDetailAndAssignments(confirmed, timeDetail);

        return new ManualScheduleAssignmentDeleteResponse(
                confirmed.getId(),
                timeDetail.getId(),
                deletedAssignmentCount,
                "DELETED"
        );
    }

    /**
     * 주간 스케줄의 활성 time_detail 목록을 날짜와 파트 번호 순서로 조회한다.
     */
    private List<TimeDetail> findActiveTimeDetails(List<Day> days) {
        List<Long> dayIds = days.stream()
                .filter(day -> !day.isHolidayStatus())
                .map(Day::getId)
                .toList();
        if (dayIds.isEmpty()) {
            return List.of();
        }
        return timeDetailRepository.findByDay_IdInAndStatusAndDeletedAtIsNullOrderByDay_DateAscWorkPartNoAsc(
                dayIds,
                TimeDetailStatus.ACTIVE
        );
    }

    /**
     * 일반 자동 스케줄링 알고리즘에 넘길 근무 상세 시간만 추린다.
     */
    private List<TimeDetail> findNormalTimeDetails(List<TimeDetail> timeDetails) {
        return timeDetails.stream()
                .filter(timeDetail -> !timeDetail.getDay().isSelectLimitStatus())
                .toList();
    }

    /**
     * 근무자가 근무 불가로 선택할 수 없어 전체 활성 근무자 랜덤 배정이 필요한 근무 상세 시간만 추린다.
     */
    private List<TimeDetail> findRandomTimeDetails(List<TimeDetail> timeDetails) {
        return timeDetails.stream()
                .filter(timeDetail -> timeDetail.getDay().isSelectLimitStatus())
                .toList();
    }

    /**
     * 근무 제출 불가 요일의 근무 상세 시간을 전체 활성 근무자 중 랜덤으로 배정해 기존 후보에 병합한다.
     */
    private List<ScheduleCandidate> appendRandomAssignments(
            List<ScheduleCandidate> candidates,
            List<TimeDetail> randomTimeDetails,
            List<Long> activeWorkerMemberIds,
            int maxPersonalWorkCount
    ) {
        if (randomTimeDetails.isEmpty() || candidates.isEmpty()) {
            return candidates;
        }

        List<ScheduleCandidate> mergedCandidates = new ArrayList<>(candidates.size());
        for (ScheduleCandidate candidate : candidates) {
            mergedCandidates.add(appendRandomAssignmentsToCandidate(
                    candidate,
                    randomTimeDetails,
                    activeWorkerMemberIds,
                    maxPersonalWorkCount
            ));
        }
        return mergedCandidates;
    }

    /**
     * 단일 후보에 랜덤 배정 슬롯을 추가한다.
     */
    private ScheduleCandidate appendRandomAssignmentsToCandidate(
            ScheduleCandidate candidate,
            List<TimeDetail> randomTimeDetails,
            List<Long> activeWorkerMemberIds,
            int maxPersonalWorkCount
    ) {
        Map<Long, List<ScheduleCandidateTimeDetail>> timeDetailsByDayId = new LinkedHashMap<>();
        Map<Long, Integer> workCountByMemberId = new HashMap<>();

        for (ScheduleCandidateDay day : candidate.days()) {
            List<ScheduleCandidateTimeDetail> copiedTimeDetails = new ArrayList<>(day.timeDetails());
            timeDetailsByDayId.put(day.dayId(), copiedTimeDetails);
            for (ScheduleCandidateTimeDetail timeDetail : copiedTimeDetails) {
                for (Long workerMemberId : timeDetail.workerMemberIds()) {
                    workCountByMemberId.merge(workerMemberId, 1, Integer::sum);
                }
            }
        }

        for (TimeDetail randomTimeDetail : randomTimeDetails) {
            List<Long> selectedWorkerMemberIds = selectRandomWorkerMemberIds(
                    activeWorkerMemberIds,
                    workCountByMemberId,
                    maxPersonalWorkCount,
                    randomTimeDetail.getWorkerCount()
            );
            timeDetailsByDayId
                    .computeIfAbsent(randomTimeDetail.getDay().getId(), ignored -> new ArrayList<>())
                    .add(new ScheduleCandidateTimeDetail(randomTimeDetail.getId(), selectedWorkerMemberIds));
            selectedWorkerMemberIds.forEach(
                    workerMemberId -> workCountByMemberId.merge(workerMemberId, 1, Integer::sum)
            );
        }

        List<ScheduleCandidateDay> days = timeDetailsByDayId.entrySet().stream()
                .map(entry -> new ScheduleCandidateDay(entry.getKey(), entry.getValue()))
                .toList();
        return new ScheduleCandidate(candidate.candidateNo(), candidate.score(), days);
    }

    /**
     * 최대 근무 횟수를 넘지 않는 활성 근무자 중 필요한 인원을 랜덤으로 선택한다.
     */
    private List<Long> selectRandomWorkerMemberIds(
            List<Long> activeWorkerMemberIds,
            Map<Long, Integer> workCountByMemberId,
            int maxPersonalWorkCount,
            int requiredWorkerCount
    ) {
        List<Long> candidates = activeWorkerMemberIds.stream()
                .filter(workerMemberId -> workCountByMemberId.getOrDefault(workerMemberId, 0) < maxPersonalWorkCount)
                .collect(Collectors.toCollection(ArrayList::new));

        if (candidates.size() < requiredWorkerCount) {
            throw new ApiException(ErrorCode.CONFLICT, "랜덤 배정 가능한 근무자가 부족합니다.");
        }

        Collections.shuffle(candidates);
        return candidates.stream()
                .limit(requiredWorkerCount)
                .toList();
    }

    private List<Long> findActiveWorkerMemberIds(Long workPlaceId) {
        return crewRepository.findMemberIdsByWorkPlaceAndRole(
                workPlaceId,
                CrewJoinStatus.APPROVED,
                CrewRole.WORKER,
                CrewStatus.ACTIVE
        );
    }

    /**
     * 활성 근무자 중 근무 불가 조건 제출을 완료한 근무자만 자동 스케줄 생성 대상으로 사용한다.
     */
    private List<Long> findSubmittedActiveWorkerMemberIds(Long workPlaceId, Long weekScheduleId, List<Long> workerMemberIds) {
        if (workerMemberIds.isEmpty()) {
            throw new ApiException(ErrorCode.CONFLICT, "자동 스케줄을 생성할 승인 근무자가 없습니다.");
        }

        List<Long> submittedMemberIds = workerSelectSubmissionRepository
                .findByWorkPlaceIdAndWeekScheduleIdAndMemberIdInAndStatusAndDeletedAtIsNull(
                        workPlaceId,
                        weekScheduleId,
                        workerMemberIds,
                        WorkerSelectSubmissionStatus.ACTIVE
                )
                .stream()
                .map(WorkerSelectSubmission::getMemberId)
                .distinct()
                .toList();

        if (submittedMemberIds.isEmpty()) {
            throw new ApiException(ErrorCode.CONFLICT, "자동 스케줄을 생성할 제출 완료 근무자가 없습니다.");
        }

        return submittedMemberIds;
    }

    /**
     * 제출된 근무 불가 time_detail을 timeDetailId 기준으로 묶는다.
     */
    private Map<Long, Set<Long>> findUnavailableWorkerIdsByTimeDetailId(
            Long workPlaceId,
            Long weekScheduleId,
            List<Long> workerMemberIds
    ) {
        List<WorkerSelectSubmission> submissions = workerSelectSubmissionRepository
                .findByWorkPlaceIdAndWeekScheduleIdAndMemberIdInAndStatusAndDeletedAtIsNull(
                        workPlaceId,
                        weekScheduleId,
                        workerMemberIds,
                        WorkerSelectSubmissionStatus.ACTIVE
                );
        if (submissions.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> memberIdBySubmissionId = submissions.stream()
                .collect(Collectors.toMap(WorkerSelectSubmission::getId, WorkerSelectSubmission::getMemberId));
        List<Long> submissionIds = submissions.stream()
                .map(WorkerSelectSubmission::getId)
                .toList();

        Map<Long, Set<Long>> unavailableWorkerIdsByTimeDetailId = new HashMap<>();
        for (WorkerUnavailableTimeDetail unavailable : workerUnavailableTimeDetailRepository.findBySubmission_IdIn(submissionIds)) {
            Long memberId = memberIdBySubmissionId.get(unavailable.getSubmission().getId());
            unavailableWorkerIdsByTimeDetailId
                    .computeIfAbsent(unavailable.getTimeDetail().getId(), ignored -> new HashSet<>())
                    .add(memberId);
        }
        return unavailableWorkerIdsByTimeDetailId;
    }

    /**
     * 후보 목록을 클라이언트가 사용할 JSON 문자열로 직렬화한다.
     */
    private String writePreviewJson(List<ScheduleCandidate> candidates) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("candidateCount", candidates.size());
            root.put("candidates", candidates);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR, "스케줄 미리보기 JSON 생성에 실패했습니다.");
        }
    }

    /**
     * 저장된 미리보기 JSON 문자열을 JsonNode로 변환한다.
     */
    private JsonNode readPreviewJson(String previewData) {
        try {
            return objectMapper.readTree(previewData);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR, "스케줄 미리보기 JSON 해석에 실패했습니다.");
        }
    }

    /**
     * 선택한 후보 번호에 해당하는 JSON 후보를 찾는다.
     */
    private JsonNode findCandidate(String previewData, Integer selectedCandidateNo) {
        JsonNode root = readPreviewJson(previewData);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "스케줄 미리보기 데이터가 올바르지 않습니다.");
        }

        for (JsonNode candidate : candidates) {
            if (Objects.equals(candidate.path("candidateNo").asInt(), selectedCandidateNo)) {
                return candidate;
            }
        }

        throw new ApiException(ErrorCode.INVALID_REQUEST, "선택한 스케줄 후보를 찾을 수 없습니다.");
    }

    /**
     * 선택한 JSON 후보를 확정 배정 엔티티 목록으로 변환한다.
     */
    private List<ConfirmedScheduleAssignment> createAssignments(
            ConfirmedWeekSchedule confirmed,
            Long workPlaceId,
            WeekSchedule weekSchedule,
            JsonNode candidate
    ) {
        Map<Long, TimeDetail> timeDetailById = findTimeDetailMap(workPlaceId, weekSchedule.getId(), candidate);
        List<ConfirmedScheduleAssignment> assignments = new ArrayList<>();

        for (JsonNode dayNode : candidate.path("days")) {
            for (JsonNode timeDetailNode : dayNode.path("timeDetails")) {
                Long timeDetailId = timeDetailNode.path("timeDetailId").asLong();
                TimeDetail timeDetail = timeDetailById.get(timeDetailId);
                if (timeDetail == null) {
                    throw new ApiException(ErrorCode.INVALID_REQUEST, "확정 후보에 잘못된 근무 시간대가 포함되어 있습니다.");
                }

                for (JsonNode workerMemberIdNode : timeDetailNode.path("workerMemberIds")) {
                    assignments.add(ConfirmedScheduleAssignment.create(
                            confirmed,
                            workPlaceId,
                            weekSchedule,
                            timeDetail.getDay(),
                            timeDetail,
                            workerMemberIdNode.asLong()
                    ));
                }
            }
        }

        return assignments;
    }

    /**
     * JSON 후보 안의 timeDetailId들이 해당 주간 스케줄에 실제로 속하는지 검증하며 조회한다.
     */
    private Map<Long, TimeDetail> findTimeDetailMap(Long workPlaceId, Long weekScheduleId, JsonNode candidate) {
        List<Long> timeDetailIds = new ArrayList<>();
        for (JsonNode dayNode : candidate.path("days")) {
            for (JsonNode timeDetailNode : dayNode.path("timeDetails")) {
                timeDetailIds.add(timeDetailNode.path("timeDetailId").asLong());
            }
        }

        List<TimeDetail> timeDetails = timeDetailRepository
                .findAllByIdInAndDay_WeekSchedule_IdAndDay_WeekSchedule_WorkPlace_IdAndStatusAndDeletedAtIsNull(
                        timeDetailIds.stream().distinct().toList(),
                        weekScheduleId,
                        workPlaceId,
                        TimeDetailStatus.ACTIVE
                );

        if (timeDetails.size() != timeDetailIds.stream().distinct().count()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "확정 후보에 잘못된 근무 시간대가 포함되어 있습니다.");
        }

        return timeDetails.stream()
                .collect(Collectors.toMap(TimeDetail::getId, timeDetail -> timeDetail));
    }

    /**
     * 확정 주간 스케줄에 속한 날짜인지 검증하고 활성 day를 조회한다.
     */
    private Day findActiveDayInConfirmedSchedule(
            ConfirmedWeekSchedule confirmed,
            ManualScheduleAssignmentRequest request
    ) {
        return dayRepository.findByWeekSchedule_IdAndDateAndStatusAndDeletedAtIsNull(
                        confirmed.getWeekSchedule().getId(),
                        request.workDate(),
                        DayStatus.ACTIVE
                )
                .orElseThrow(() -> new ApiException(
                        ErrorCode.INVALID_REQUEST,
                        "확정된 주간 스케줄 기간에 포함되지 않는 날짜입니다."
                ));
    }

    /**
     * 확정 주간 스케줄에 속한 활성 time_detail인지 검증하고 조회한다.
     */
    private TimeDetail findActiveTimeDetailInConfirmedSchedule(
            ConfirmedWeekSchedule confirmed,
            Long timeDetailId
    ) {
        return timeDetailRepository
                .findByIdAndDay_WeekSchedule_IdAndDay_WeekSchedule_WorkPlace_IdAndStatusAndDeletedAtIsNull(
                        timeDetailId,
                        confirmed.getWeekSchedule().getId(),
                        confirmed.getWorkPlaceId(),
                        TimeDetailStatus.ACTIVE
                )
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "근무 파트를 찾을 수 없습니다."));
    }

    /**
     * 단건 근무 파트 추가/수정 요청의 업무 규칙을 검증한다.
     */
    private void validateManualAssignmentRequest(
            Long workPlaceId,
            Long dayId,
            ManualScheduleAssignmentRequest request
    ) {
        validateTimeRange(request);
        validateWorkerMemberIds(workPlaceId, request.workerMemberIds());

        if (timeDetailRepository.existsByDay_IdAndWorkPartNoAndStatusAndDeletedAtIsNull(
                dayId,
                request.workPartNo(),
                TimeDetailStatus.ACTIVE
        )) {
            throw new ApiException(ErrorCode.CONFLICT, "같은 날짜에 동일한 근무 파트 번호가 이미 존재합니다.");
        }
    }

    /**
     * 근무 시작 시간이 종료 시간보다 빠른지 검증한다.
     */
    private void validateTimeRange(ManualScheduleAssignmentRequest request) {
        if (!request.startTime().isBefore(request.closeTime())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "근무 시작 시간은 종료 시간보다 빨라야 합니다.");
        }
    }

    /**
     * 배정할 근무자들이 중복 없이 해당 사업장의 승인 근무자인지 검증한다.
     */
    private void validateWorkerMemberIds(Long workPlaceId, List<Long> workerMemberIds) {
        Set<Long> uniqueWorkerMemberIds = new LinkedHashSet<>(workerMemberIds);
        if (uniqueWorkerMemberIds.size() != workerMemberIds.size()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "동일한 근무자를 중복 배정할 수 없습니다.");
        }

        List<Long> activeWorkerMemberIds = findActiveWorkerMemberIds(workPlaceId);
        if (!activeWorkerMemberIds.containsAll(uniqueWorkerMemberIds)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "해당 사업장에 승인된 근무자만 배정할 수 있습니다.");
        }
    }

    /**
     * 확정 근무 파트의 근무자별 확정 배정 row를 저장한다.
     */
    private void saveManualAssignments(
            ConfirmedWeekSchedule confirmed,
            Long workPlaceId,
            TimeDetail timeDetail,
            List<Long> workerMemberIds
    ) {
        List<ConfirmedScheduleAssignment> assignments = workerMemberIds.stream()
                .map(workerMemberId -> ConfirmedScheduleAssignment.create(
                        confirmed,
                        workPlaceId,
                        confirmed.getWeekSchedule(),
                        timeDetail.getDay(),
                        timeDetail,
                        workerMemberId
                ))
                .toList();
        confirmedScheduleAssignmentRepository.saveAll(assignments);
    }

    /**
     * time_detail과 그 time_detail에 묶인 활성 확정 배정을 삭제 처리한다.
     */
    private int deleteTimeDetailAndAssignments(
            ConfirmedWeekSchedule confirmed,
            TimeDetail timeDetail
    ) {
        LocalDateTime now = LocalDateTime.now();
        List<ConfirmedScheduleAssignment> assignments = confirmedScheduleAssignmentRepository
                .findByConfirmedWeekSchedule_IdAndTimeDetail_IdAndStatusAndDeletedAtIsNullOrderByIdAsc(
                        confirmed.getId(),
                        timeDetail.getId(),
                        ConfirmedScheduleAssignmentStatus.ACTIVE
                );

        assignments.forEach(assignment -> assignment.markDeleted(now));
        timeDetail.markDeleted(now);
        return assignments.size();
    }

    /**
     * 로그인한 회원을 활성 상태 기준으로 조회한다.
     */
    private Member findActiveMember(Long memberId) {
        return memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
    }

    /**
     * 요청자가 해당 사업장의 소유자인지 검증하며 사업장을 조회한다.
     */
    private WorkPlace findOwnedActiveWorkPlace(Long workPlaceId, Long ownerMemberId) {
        WorkPlace workPlace = workPlaceRepository.findByIdAndStatusAndDeletedAtIsNull(
                        workPlaceId,
                        WorkPlaceStatus.ACTIVE
                )
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "사업장을 찾을 수 없습니다."));

        if (!workPlace.getOwnerMemberId().equals(ownerMemberId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "해당 사업장에 대한 권한이 없습니다.");
        }
        return workPlace;
    }

    /**
     * 사업장 하위의 활성 주간 스케줄 조건을 조회한다.
     */
    private WeekSchedule findActiveWeekSchedule(Long weekScheduleId, Long workPlaceId) {
        return weekScheduleRepository.findByIdAndWorkPlaceIdAndStatusAndDeletedAtIsNull(
                        weekScheduleId,
                        workPlaceId,
                        WeekScheduleStatus.ACTIVE
                )
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "주간 스케줄 조건을 찾을 수 없습니다."));
    }

    /**
     * 사업장에 속한 활성 확정 주간 스케줄을 조회한다.
     */
    private ConfirmedWeekSchedule findActiveConfirmedWeekSchedule(Long confirmedWeekScheduleId, Long workPlaceId) {
        return confirmedWeekScheduleRepository.findByIdAndWorkPlaceIdAndStatusAndDeletedAtIsNull(
                        confirmedWeekScheduleId,
                        workPlaceId,
                        ConfirmedWeekScheduleStatus.ACTIVE
                )
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "확정된 주간 스케줄을 찾을 수 없습니다."));
    }

    /**
     * 같은 주간 스케줄에 사용 가능한 자동 생성 결과가 이미 있으면 일반 생성을 막는다.
     */
    private void validateNoActiveGeneratedRun(Long weekScheduleId) {
        boolean exists = scheduleGenerationRunRepository.existsByWeekSchedule_IdAndStatusAndDeletedAtIsNull(
                weekScheduleId,
                ScheduleGenerationRunStatus.GENERATED
        );
        if (exists) {
            throw new ApiException(ErrorCode.CONFLICT, "이미 생성된 자동 스케줄 미리보기가 있습니다. 재생성 API를 사용해주세요.");
        }
    }

    /**
     * 재생성 전에 기존 활성 자동 생성 이력과 미리보기 스냅샷을 삭제 처리한다.
     */
    private void markActiveGeneratedRunsAndPreviewsDeleted(Long weekScheduleId) {
        List<ScheduleGenerationRun> activeRuns =
                scheduleGenerationRunRepository.findByWeekSchedule_IdAndStatusAndDeletedAtIsNull(
                        weekScheduleId,
                        ScheduleGenerationRunStatus.GENERATED
                );
        if (activeRuns.isEmpty()) {
            return;
        }

        LocalDateTime deletedAt = LocalDateTime.now();
        List<Long> activeRunIds = activeRuns.stream()
                .map(ScheduleGenerationRun::getId)
                .toList();

        schedulePreviewRepository.findByScheduleGenerationRun_IdInAndStatusAndDeletedAtIsNull(
                        activeRunIds,
                        SchedulePreviewStatus.ACTIVE
                )
                .forEach(preview -> preview.markDeleted(deletedAt));
        activeRuns.forEach(run -> run.markDeleted(deletedAt));
    }

    /**
     * 생성 완료 상태의 자동 생성 실행 이력을 조회한다.
     */
    private ScheduleGenerationRun findGeneratedRun(Long runId, Long weekScheduleId) {
        return scheduleGenerationRunRepository.findByIdAndWeekSchedule_IdAndStatusAndDeletedAtIsNull(
                        runId,
                        weekScheduleId,
                        ScheduleGenerationRunStatus.GENERATED
                )
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "자동 스케줄 생성 이력을 찾을 수 없습니다."));
    }
}
