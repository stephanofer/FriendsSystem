# Documentación de producto — CraftKit

Esta carpeta contiene documentación técnica de producto para los módulos de CraftKit. Está pensada para desarrolladores de plugins consumidores y para futuros mantenedores de la librería.

CraftKit es una librería interna modular. Cada módulo se documenta por separado para que la documentación pueda crecer sin mezclar responsabilidades.

## Índice de módulos

| Módulo | Estado | Documentación |
| --- | --- | --- |
| `craftkit-database` | Implementado | [Base de datos MySQL](./craftkit-database/README.md) |
| `craftkit-feedback` | Implementado | [Feedback localizado para Paper/Adventure](./craftkit-feedback/README.md) |
| `craftkit-redis` | Implementado | [Redis, caché, Pub/Sub y coordinación](./craftkit-redis/README.md) |
| `craftkit-zmenu` | Implementado | [Integración con zMenu](./craftkit-zmenu/README.md) |

## Convenciones de esta documentación

- La documentación describe el comportamiento real del código actual.
- Los ejemplos están enfocados en plugins consumidores de CraftKit.
- Las secciones de cada módulo separan conceptos, configuración, flujos, APIs y advertencias operativas.
