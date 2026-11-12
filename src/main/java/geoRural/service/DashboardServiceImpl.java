package geoRural.service;

import geoRural.dto.DashboardResponse;
import geoRural.repository.DashboardRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final DashboardRepository dashboardRepository;

    public DashboardServiceImpl(DashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    @Override
    public DashboardResponse consultar() {

        DashboardResponse.Resumo resumo = new DashboardResponse.Resumo(
                dashboardRepository.contarFontesAtivas(),
                dashboardRepository.contarExecucoesUltimos7Dias(),
                dashboardRepository.contarFalhasUltimos7Dias(),
                dashboardRepository.contarRegistrosQuarentena(),
                dashboardRepository.contarRegistrosZonaTratada()
        );

        List<DashboardResponse.ExecucaoRecente> execucoesRecentes =
                dashboardRepository.listarExecucoesRecentes()
                        .stream()
                        .map(execucao -> new DashboardResponse.ExecucaoRecente(
                                execucao.fonte(),
                                execucao.tipo(),
                                execucao.status(),
                                execucao.registrosLidos(),
                                execucao.registrosValidos(),
                                execucao.registrosInvalidos(),
                                execucao.iniciadaEm()
                        ))
                        .toList();

        return new DashboardResponse(resumo, execucoesRecentes);
    }
}