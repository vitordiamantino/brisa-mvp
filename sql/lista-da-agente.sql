-- Lista da agente de saúde: quem está em laranja ou vermelho e ainda não teve contato humano hoje.
-- Contato humano = o idoso respondeu "Estou bem" ou alguém da rede confirmou que falou com ele.
-- Quem pediu ajuda vem primeiro; depois, do maior IRT para o menor.
SELECT i.NOME,
       i.BAIRRO,
       i.IRT,
       i.NIVEL,
       (SELECT c.STATUS
          FROM BRISA_CHECKIN c
         WHERE c.IDOSO_ID = i.ID
         ORDER BY c.ENVIADO_EM DESC
         FETCH FIRST 1 ROW ONLY) AS ULTIMO_CHECKIN
  FROM BRISA_IDOSO i
 WHERE i.NIVEL IN ('LARANJA', 'VERMELHO')
   AND NOT EXISTS (
         SELECT 1
           FROM BRISA_CHECKIN c
          WHERE c.IDOSO_ID = i.ID
            AND c.ENVIADO_EM >= TRUNC(SYSDATE)
            AND (c.STATUS = 'ESTOU_BEM'
                 OR (c.STATUS = 'CONFIRMADO_POR_CONTATO' AND c.RESOLVIDO_POR <> 'substituído por novo check-in')))
 ORDER BY CASE WHEN EXISTS (SELECT 1 FROM BRISA_CHECKIN c
                             WHERE c.IDOSO_ID = i.ID AND c.STATUS = 'PEDIU_AJUDA'
                               AND c.ENVIADO_EM >= TRUNC(SYSDATE)) THEN 0 ELSE 1 END,
          i.IRT DESC;
